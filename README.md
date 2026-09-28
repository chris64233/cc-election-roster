# cc-election-roster

选举、选区与选民名册管理服务：选民名册核验、选票签发（正式/临时/邮寄）、投票提交消费、临时票裁定、**邮寄票/临时票补正**与选区汇总。

当前包含可启动的服务入口、持久化依赖、应用上下文测试以及完整的选举流程（含补正与跨渠道并发）自动化测试。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1
- H2（内存库，默认随应用启动；测试独立实例）

迁移项目沿用现有 Spring Boot 版本，其他项目使用上述版本。

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 主要业务规则

### 1. 名册与选票样式

- 一次选举（Election）包含多个选区（District），每个选区绑定一种选票样式（BallotStyle）。
- 选民（Voter）登记在某选举的某选区，名册状态为 `ELIGIBLE` / `DISPUTED` / `INELIGIBLE`。

### 2. 签发：一人一票

- 同一选民在同一次选举中**最多取得一张票**。正式签发（`OFFICIAL`）、临时签发（`PROVISIONAL`）、邮寄票登记（`MAIL`）共享同一条数据库唯一约束 `(election_id, voter_id)`，没有绕过路径。
- 签发前核验：选民须在册；`INELIGIBLE` 一律拒绝；`DISPUTED` 只能签发临时票；可选传入投票点核验选区码，与名册不一致即拒绝。
- 签发成功后生成不可修改的签发凭证（随机 `credentialToken`），凭证记录只能做单向状态流转：`ISSUED → CONSUMED / VOIDED`。

### 3. 事件号幂等与并发

- 每次签发带**签发事件号** `eventNo`，对 `(election_id, event_no)` 有唯一约束：相同事件号重放返回首次结果；事件号被其他选民/类型占用返回 409。
- 不同投票点**并发**为同一选民签发时，由共享唯一约束保证最多一个事务成功，其余收到 409。

### 4. 临时票：内容与身份分离、裁定决定去向

- 临时票的身份核验信息（争议原因、核验材料说明）保存在 `provisional_records`；票面选择保存在匿名的 `ballot_contents`（随机 ballotId，不含选民/凭证引用），二者**物理隔离**。
- 临时票裁定一次性、不可逆：
  - `ACCEPTED`：选票计入选区（提交前通过的，提交时直接计入）；
  - `REJECTED`：已提交的选票永久作废（`voided=true`，不计汇总）；尚未提交的凭证立即作废，再提交被拒绝。

### 5. 投票提交：凭证消费一次

- 一张有效凭证只能消费一次（`submission_records` 对凭证唯一约束）。
- 用同一凭证重放：**内容相同**（SHA-256 一致）返回原回执（`duplicate=true`）；**内容变化**返回 409 冲突。
- 已计入（`counted=true`）的选票不可修改、不可撤回、不可作废。

### 6. 补正：恢复原选票，不签发第二张票

邮寄票登记时身份材料不全（`identityIncomplete=true`），或临时票资格争议待裁定时，选票**暂不计入**并进入补正流程：

- **提交新材料（截止前）**：`POST /api/cures/submissions` 生成版本化的 `cure_records`（issuanceId + version），始终关联**原选票**的签发记录；旧的待确认版本标记为 SUPERSEDED。补正材料必须在选举 `cureDeadline` 前提交，逾期直接按截止裁定失败。
- **确认补正**：`POST /api/cures/confirmations` 作最终决定前重新核验：① 是否超过截止时间；② 选民当前状态必须恢复为 `ELIGIBLE`；③ 名册选区（及可选的现场核验选区码）与原选票一致；④ 未通过其他渠道有效投票。任一不满足即补正失败。
- **补正只恢复原选票**：确认通过后，原选票若已提交则计入选区；尚未提交的，原凭证保持有效、提交时直接计入。**不会重新签发凭证、不会产生第二张票**。失败时已提交的原选票 `voided=true`，未提交的凭证作废。
- **截止裁定**：`POST /api/elections/{id}/deadline-ruling` 将截止时仍未完成补正的暂存邮寄票/PENDING 临时票一律裁定失败。
- **跨渠道唯一结果**：`effective_votes` 对 `(选举,选民)` 唯一，配合签发记录行级写锁，使补正确认、其他渠道投票登记、临时票裁定、截止裁定并发时只有一个事务能登记有效结果（来源 `CURE_CONFIRMED` / `EXTERNAL_CHANNEL` / `PROVISIONAL_ACCEPTED` / `DIRECT_SUBMISSION`），跨渠道凭证消费原子完成。其他渠道登记：`POST /api/elections/{id}/external-votes`（同一 channelRef 重放幂等；已有有效结果时其他渠道 409）。
- **材料与票面隔离**：补正材料只保存身份侧说明与版本状态，与 `ballot_contents` 之间没有外键或对象导航；审计 detail 只记版本、选区、contentHash 等摘要，状态/查询接口只暴露流程事实，**不得由材料记录推断或暴露投票内容**。

### 7. 查询与审计

- `选民签发状态`：返回名册状态、签发类型/状态、邮寄票补正状态（cureStatus）、临时票裁定结果与该选民唯一有效结果来源（effectiveVoteSource），不含票面选择。
- `选区汇总`：签发总数、已消费凭证数、已计入选票数、待裁定临时票数、待补正邮寄票数。
- `审计链`：追加式哈希链，`eventHash = SHA-256(prevHash|eventType|refId|detail)`，可独立重算校验；detail 只含类型、选区、`contentHash` 等摘要，**绝不记录票面选择内容**。

## HTTP 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/issuances` | 签发正式/临时票或登记邮寄票（请求体含 electionId、voterRef、eventNo、type、pollingPlace；临时票加 identityNotes；邮寄票可加 identityIncomplete=true 进入补正；可选 expectedDistrictCode） |
| POST | `/api/submissions` | 凭 credentialToken 提交 choicesJson；重放同内容返回原回执，内容变化 409；材料不全暂不计入 |
| POST | `/api/provisionals/{issuanceId}/adjudication` | 临时票裁定（accepted + reason） |
| POST | `/api/cures/submissions` | 截止前提交补正材料（issuanceId + materialNotes），返回新版本号；逾期/终态返回失败 |
| POST | `/api/cures/confirmations` | 确认补正（issuanceId，可选 version、expectedDistrictCode）：重新核验截止/状态/选区/跨渠道投票 |
| POST | `/api/elections/{electionId}/deadline-ruling` | 截止裁定：未完成补正的暂存选票全部作废 |
| POST | `/api/elections/{electionId}/external-votes` | 登记其他渠道有效投票（voterRef + channelRef）；与补正确认竞争唯一结果 |
| GET | `/api/elections/{electionId}/voters/{voterRef}/status` | 选民签发状态（含 cureStatus、effectiveVoteSource） |
| GET | `/api/districts/{districtId}/summary` | 选区汇总（含 pendingCures） |
| GET | `/api/audit` | 哈希链审计事件（无票面内容） |
| POST | `/api/admin/voters` | 演示/测试用：登记选举、选区、样式与选民（按 code 幂等复用） |
| POST | `/api/admin/elections/{electionId}/cure-deadline?deadline=ISO-8601` | 设置补正截止时间 |
| POST | `/api/admin/elections/{electionId}/voters/{voterRef}/roster?status=&districtCode=` | 演示/测试用：名册状态/选区修正 |

## 数据模型

- `elections`：选举基础数据；`cure_deadline` 为补正截止时间。
- `ballot_styles` / `districts` / `voters`：选票样式、选区与名册。
- `issuances`：签发凭证；唯一约束 `(election,voter)`、`(election,event_no)`、`credential_token`；`cure_status` 记录邮寄票补正状态（NOT_REQUIRED/PENDING/CONFIRMED/FAILED）。
- `provisional_records`：临时票身份侧信息与裁定（PENDING/ACCEPTED/REJECTED）。
- `cure_records`：补正材料版本（issuanceId + version 唯一；PENDING/CONFIRMED/FAILED/SUPERSEDED），只含身份侧说明，与票面无外键。
- `ballot_contents`：匿名票面选择，仅以随机 ballotId 标识，`counted`/`voided` 标记去向。
- `submission_records`：凭证消费记录（凭证唯一），保存 contentHash 与回执号。
- `effective_votes`：有效投票结果槽位，`(election,voter)` 唯一，是跨渠道原子仲裁点。
- `audit_events`：哈希链审计事件，只存摘要。
