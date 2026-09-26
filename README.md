# cc-election-roster

选举、选区与选民名册管理服务：选民名册核验、选票签发（正式/临时/邮寄）、投票提交消费、临时票裁定与选区汇总。

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

### 6. 查询与审计

- `选民签发状态`：返回名册状态、签发类型/状态、投票点与临时票裁定结果，不含票面选择。
- `选区汇总`：签发总数、已消费凭证数、已计入选票数、待裁定临时票数。
- `审计链`：追加式哈希链，`eventHash = SHA-256(prevHash|eventType|refId|detail)`，可独立重算校验；detail 只含类型、选区、`contentHash` 等摘要，**绝不记录票面选择内容**。

## HTTP 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/issuances` | 签发正式/临时票或登记邮寄票（请求体含 electionId、voterRef、eventNo、type、pollingPlace，临时票加 identityNotes，可选 expectedDistrictCode） |
| POST | `/api/submissions` | 凭 credentialToken 提交 choicesJson；重放同内容返回原回执，内容变化 409 |
| POST | `/api/provisionals/{issuanceId}/adjudication` | 临时票裁定（accepted + reason） |
| GET | `/api/elections/{electionId}/voters/{voterRef}/status` | 选民签发状态 |
| GET | `/api/districts/{districtId}/summary` | 选区汇总 |
| GET | `/api/audit` | 哈希链审计事件（无票面内容） |
| POST | `/api/admin/voters` | 演示/测试用：登记选举、选区、样式与选民（按 code 幂等复用） |

## 数据模型

- `elections` / `ballot_styles` / `districts` / `voters`：选举基础数据与名册。
- `issuances`：签发凭证；唯一约束 `(election,voter)`、`(election,event_no)`、`credential_token`。
- `provisional_records`：临时票身份侧信息与裁定（PENDING/ACCEPTED/REJECTED）。
- `ballot_contents`：匿名票面选择，仅以随机 ballotId 标识，`counted`/`voided` 标记去向。
- `submission_records`：凭证消费记录（凭证唯一），保存 contentHash 与回执号。
- `audit_events`：哈希链审计事件，只存摘要。
