# cc-election-roster

选举、选区与选民名册管理服务：选民名册核验、选票签发（正式/临时/邮寄）、投票提交消费、临时票裁定、身份材料补正（恢复原票）、跨渠道投票裁决与选区汇总。

当前包含可启动的服务入口、持久化依赖、应用上下文测试以及完整的选举流程自动化测试。

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

### 6. 身份材料补正（ballot cure）：只恢复原票、唯一有效结果

邮寄票或临时票提交时若声明身份材料不全（`identityIncomplete=true`），选票进入**暂存**（`ballot_contents.held=true`，不计入），并针对**原签发**开启一条补正记录：

- **提交新材料**：在补正截止时间（`elections.cure_deadline`）前可多次提交，形成递增版本，全部关联回原补正记录/原选票。**不会重新签发第二张票**（仍是原凭证、原 `issuance`，`(election,voter)` 唯一约束也不允许）。
- **补正确认**才恢复计入；确认前在同一事务内重新检查：
  - 是否已过补正截止时间；
  - 选民名册状态（变为 `INELIGIBLE` 即拒绝）；
  - 名册选区是否仍与原选票选区一致；
  - 是否已通过其他渠道有效投票；并要求至少提交过一份新材料。
- 确认通过：原暂存选票置 `counted=true`（恢复的是同一张票）；逾期/资格不符/选区不符/已在别处投票：原选票永久作废，补正进入对应终态（`CONFIRMED/REJECTED/EXPIRED/SUPERSEDED`），终态不可逆。

**并发与原子性**：补正确认、其他渠道有效投票登记、截止裁定三类操作竞争同一选民行锁（`SELECT … FOR UPDATE`），跨渠道凭证消费与本地选票处置在同一事务原子完成；`external_vote_records` 对 `(election,voter)` 唯一约束兜底。因此同一选民在补正确认、其他渠道投票、截止裁定之间**最多保留一个有效结果**，已计入的本地选票会拒绝外部登记，反之暂存选票会被作废。

**隔离**：补正材料（`cure_materials`/`cure_records`）只存身份侧说明与材料哈希，与匿名的 `ballot_contents` 之间无外键、无内容关联；状态/汇总/审计接口只暴露补正**状态**与哈希，不得由材料记录推断或暴露票面选择。

### 7. 查询与审计

- `选民签发状态`：返回名册状态、签发类型/状态、投票点、临时票裁定结果与补正状态（`cureStatus`），不含票面选择与补正材料明细。
- `选区汇总`：签发总数、已消费凭证数、已计入选票数、待裁定临时票数、待补正数（`pendingCures`）、暂存选票数（`heldBallots`）。
- `审计链`：追加式哈希链，`eventHash = SHA-256(prevHash|eventType|refId|detail)`，可独立重算校验；detail 只含类型、选区、`contentHash`、`materialHash` 等摘要，**绝不记录票面选择内容或材料正文**。

## HTTP 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/issuances` | 签发正式/临时票或登记邮寄票（请求体含 electionId、voterRef、eventNo、type、pollingPlace，临时票加 identityNotes，可选 expectedDistrictCode） |
| POST | `/api/submissions` | 凭 credentialToken 提交 choicesJson；重放同内容返回原回执，内容变化 409。邮寄/临时票加 `identityIncomplete`/`missingMaterials` 则选票暂存待补正 |
| POST | `/api/provisionals/{issuanceId}/adjudication` | 临时票裁定（accepted + reason） |
| POST | `/api/issuances/{issuanceId}/cure/materials` | 截止前提交补正新材料（materialNotes，可选 materialContent，仅存哈希）；返回递增版本 |
| POST | `/api/issuances/{issuanceId}/cure/confirm` | 补正确认：复核截止/资格/选区/其他渠道投票后恢复原选票；失败 409 |
| POST | `/api/elections/{electionId}/external-votes` | 登记其他渠道有效投票（voterRef、channel、externalRef）；原子处置本地待决选票，同凭证重放幂等 |
| POST | `/api/cures/expire` | 截止裁定：作废所有逾期未补正的暂存选票 |
| GET | `/api/elections/{electionId}/voters/{voterRef}/status` | 选民签发状态（含 adjudication、cureStatus） |
| GET | `/api/districts/{districtId}/summary` | 选区汇总（含 pendingCures、heldBallots） |
| GET | `/api/audit` | 哈希链审计事件（无票面内容、无材料正文） |
| POST | `/api/admin/voters` | 演示/测试用：登记选举、选区、样式与选民（按 code 幂等复用，可选 cureDeadline 设置补正截止时间） |

## 数据模型

- `elections` / `ballot_styles` / `districts` / `voters`：选举基础数据与名册。
- `issuances`：签发凭证；唯一约束 `(election,voter)`、`(election,event_no)`、`credential_token`。
- `provisional_records`：临时票身份侧信息与裁定（PENDING/ACCEPTED/REJECTED）。
- `ballot_contents`：匿名票面选择，仅以随机 ballotId 标识，`counted`/`voided`/`held` 标记去向。
- `submission_records`：凭证消费记录（凭证唯一），保存 contentHash 与回执号。
- `cure_records`：补正记录（身份侧，按 issuance 唯一），状态 PENDING/CONFIRMED/REJECTED/SUPERSEDED/EXPIRED，含截止时间快照。
- `cure_materials`：补正材料版本（只存说明与 materialHash），关联回原补正记录。
- `external_vote_records`：其他渠道有效投票，`(election,voter)` 唯一，跨渠道凭证消费据此裁决。
- `audit_events`：哈希链审计事件，只存摘要。
