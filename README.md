# cc-election-roster

选举、选区与选民名册管理服务，覆盖一次选举中的选民名册核验、选票签发、
投票提交与临时票裁定全流程。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1

迁移项目沿用现有 Spring Boot 版本，其他项目使用上述版本。

## 主要业务规则

### 选举结构

- 一次选举（Election）包含多个选区（Precinct）和多种选票样式（BallotStyle）。
- 名册中的每位选民（Voter）归属于一个选区、对应一种选票样式，
  并具有资格状态：`ELIGIBLE`（有效）、`DISPUTED`（争议）、`INELIGIBLE`（无效）。

### 选票签发

- 同一选民在一次选举中只能取得一张签发凭证；正式签发（OFFICIAL）、
  临时签发（PROVISIONAL）与邮寄票登记（MAIL）共享数据库唯一约束
  `(election_id, voter_id)`。
- 签发时核验选民状态与所属选区（请求声明的选区必须与名册一致）：
  - `ELIGIBLE`：可签发正式票或登记邮寄票；
  - `DISPUTED`：只能签发临时票，且必须同时登记身份核验信息
    （核验方式、核验人），身份核验记录与票面内容分表保存；
  - `INELIGIBLE`：禁止签发（HTTP 422）。
- 签发凭证一旦创建不可修改（核心字段 `updatable = false`，无更新入口）。
- 签发事件号（`eventId`）是幂等键：重复提交同一事件号返回原签发结果；
  不同投票点并发为同一选民签发时，由唯一约束兜底，最多一个事务成功，
  其余收到 409 冲突。

### 投票提交

- 每次提交必须消费一张有效签发凭证，凭证只能被消费一次
  （状态机 + 乐观锁保证并发下最多一个提交事务成功）。
- 重复提交相同内容：幂等返回原结果（HTTP 200）；内容发生变化：返回 409 冲突。
- 正式票/邮寄票提交后直接计入对应选区；临时票提交后进入待裁定状态，
  裁定前不计入选区汇总。
- 已计入的选票（CastBallot）不可修改、不可撤回；该表不保存选民或签发凭证外键，
  票面选择与身份信息在存储层隔离。

### 临时票裁定

- 只有 `PENDING` 状态的临时票可以裁定，裁定结果不可更改。
- 裁定通过：票面内容计入对应选区（生成不关联选民身份的 CastBallot）。
- 裁定拒绝：临时票永久作废，对应签发凭证一并作废，永不计入汇总。

### 审计与查询

- 所有关键动作（签发、提交、裁定）写入仅追加的审计链：
  每条记录通过 `prevHash`/`entryHash`（SHA-256）与前序记录链接，
  只记录动作与引用标识，绝不记录票面选择内容。
- 查询接口保持票面选择与身份信息隔离：
  - `GET /api/elections/{id}/voters/{voterRef}/issuance-status`：选民签发状态（不含票面内容）；
  - `GET /api/elections/{id}/provisional-ballots[?status=]`：临时票裁定状态（不含票面内容与选民身份）；
  - `GET /api/elections/{id}/precincts/{code}/summary`：选区汇总计数（不含票面内容与选民身份）。

## 主要 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/elections` | 创建选举 |
| POST | `/api/elections/{id}/precincts` | 创建选区 |
| POST | `/api/elections/{id}/ballot-styles` | 创建选票样式 |
| POST | `/api/elections/{id}/voters` | 登记名册选民 |
| POST | `/api/elections/{id}/issuances` | 签发选票（正式/临时/邮寄，事件号幂等） |
| POST | `/api/elections/{id}/submissions` | 提交选票（消费凭证，内容幂等） |
| POST | `/api/elections/{id}/provisional-ballots/{pid}/adjudication` | 裁定临时票 |
| GET | `/api/elections/{id}/voters/{voterRef}/issuance-status` | 选民签发状态 |
| GET | `/api/elections/{id}/provisional-ballots` | 临时票裁定查询 |
| GET | `/api/elections/{id}/precincts/{code}/summary` | 选区汇总 |

错误约定：资源不存在 404，业务冲突（重复签发、内容变化、已作废、已裁定）409，
资格/状态不合法 422。

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run
