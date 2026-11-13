# 维护命令、库存事务与仪表异常周期

本文记录 `cmms-fork` 在 2026-11-14 的领域实现与验证边界。主线仍是设备维修：读数异常生成工单，工单领用或归还零件，关键变化进入通知与设备工单统计。保留 Java 17、Spring Boot 3、JPA 和 PostgreSQL；库存与异常周期状态由数据库事务维护。

本次收敛的是下表列出的 HTTP 写入口及其命令服务。它不代表所有旧控制器、导入、定时任务、工作流或直接调用底层服务的路径都已完成同等审查，也不代表全部历史数据问题已修复。

## 1. 接口与 Idempotency-Key 契约

现有 URL、请求体和成功响应形状保持兼容，新增可选请求头 `Idempotency-Key`。推荐为每一次业务操作生成 UUID；同一次操作因网络结果不明而重试时继续使用原 key。

| 方法与路径 | 请求含义 | 命令 namespace | 成功响应 |
| --- | --- | --- | --- |
| `POST /part-quantities` | `part`、`quantity`，且 `workOrder` / `purchaseOrder` 恰有一个 | `INVENTORY` | `PartQuantityShowDTO` |
| `PATCH /part-quantities/{id}` | `quantity` 为新的绝对数量，不是增量 | `INVENTORY` | `PartQuantityShowDTO` |
| `DELETE /part-quantities/{id}` | 删除关联，工单零件归还完整剩余领用量 | `INVENTORY` | `SuccessResponse` |
| `PATCH /part-quantities/work-order/{id}` | 完整目标零件 ID 列表；新增零件数量为 1，保留项维持数量，移除项归还 | `INVENTORY` | `PartQuantityShowDTO[]` |
| `PATCH /part-quantities/purchase-order/{id}` | 完整目标 `part` + `quantity` 列表；编辑采购明细暂不增加库存 | `INVENTORY` | `PartQuantityShowDTO[]` |
| `POST /parts/{id}/restock` | 正数 `quantity`，可带 `description` | `RESTOCK` | `SuccessResponse` |
| `PATCH /purchase-orders/{id}/respond?approved=true` | 同意时补库并更新采购状态；`false` 为拒绝 | `PURCHASE_RESPONSE` | `PurchaseOrderShowDTO` |
| `POST /readings` | `meter.id` 与有限数值 `value` | `READING` | `Reading` |

key 规则及边界：

- 合法字符为 `[A-Za-z0-9._:-]`，长度 1–128；格式错误返回 400。请求不带 key 仍可调用，但没有跨请求重试去重保证。
- 唯一范围是 `(company_id, namespace, command_key)`。同范围下的 key 绑定操作者、操作类型、目标和业务参数。换操作者或改变业务负载继续使用该 key 返回 409。多个库存路由共用 `INVENTORY`，不能把一个 key 当作一批不同操作的编号。
- PostgreSQL 事务级 advisory lock 串行处理同 key 的并发请求。结果记录与业务写入同事务提交；回滚后没有成功记录，可以用同 key 重试。
- 已成功的相同请求返回原始结果快照，不再扣库、补库、创建读数或推进异常状态。读数重放先于频率检查，故网络重试不会被新读数的频率限制误拒绝。
- 重放前仍检查当前操作者、所属公司和目标权限；已失权用户不能靠旧 key 取回结果。父工单、采购单或仪表被删除后也不保证还能重放。零件关联本身被删后的删除重试，可以依据记录中的父对象重新授权。
- 目前没有自动 TTL 清理。自行删除 `domain_command` 会丢失相应去重记忆，保留策略需要与业务重试窗口一起设计。
- 这是数据库命令结果的幂等，不是所有 HTTP 请求、所有外部通知的“恰好一次”。`PATCH/DELETE /readings/{id}` 没有纳入该命令凭证契约。

Web 与移动端的通用 API 包装器已为这些写入口传递 key。超时、断连或 5xx 后在当前进程内保留 key；成功或明确 4xx 后清除，使后续有意执行的同值操作获得新 key。待重试状态存在内存中，页面刷新、应用重启或退出清理后不会自动恢复；需要跨重启重试的客户端应自行持久化操作 key。

## 2. 库存总事务与返料

`InventoryCommandService` 在一次事务内完成权限检查、锁定、库存/关联/流水变更及成功凭证写入。HTTP 入口通过 `FreshMaintenanceActor` 从独立持久化上下文读取当前用户、角色与订阅；命令内再检查资源所属公司及现有 `canBeEditedBy` / `canBeViewedBy` 规则。内部调用者仍须显式提供当前授权域，不能假设任意 `User` 对象都是新鲜权限。

工单与采购命令先锁父对象，再按零件 ID 升序锁零件。单条修改、批量替换和采购审批都会在父锁后刷新已加载的 `PartQuantity`；零件本身也在锁后刷新。JPA 查询可能返回一级缓存中的旧对象，只有取得锁而不刷新，仍可能按旧数量计算返料。原有 `Part.version` 乐观锁继续保留。

数量规则：

- 工单变更使用 `delta = 新关联数量 - 原关联数量`，实际库存为 `原库存 - delta`。
- `delta = 0` 不产生库存扣减、补库或新的库存流水；命令成功凭证仍可记录。
- 归还零件追加负数 `PartTransaction`，保留先前领用流水；不再通过删旧流水表达返料。
- 库存不足时拒绝整个命令。数量须有限且非负，没有单位的零件须为整数，不再静默截断小数；手动补库须大于 0。
- 非库存零件延续原有行为：可以维护关联，但不扣减库存，也不写库存流水。
- 采购明细编辑不补库。审批通过时，状态、所有零件补库和流水共同提交；同 key 重试不重复补库，已通过订单不能再用新命令修改明细或重复审批。

例如初始库存 10：领用 3 后库存 7；将领用改为 5 后库存 5；删除关联或从完整目标列表中移除后库存恢复 10。流水依次为 `+3、+2、-5`。事务中任一步失败时，库存、关联、流水与命令记录一起回滚。

低库存提醒及零件库存变化 Webhook 目前在提交后尽力执行，失败不会把已提交的库存操作伪装成回滚。它们尚未纳入持久化 Outbox 投递，因此不能据此承诺宕机不丢通知。仪表/工单事件的可靠链路另见 [可靠事件说明](reliable-events-implementation.md)。

## 3. 仪表异常周期

`ReadingService.create` 验证仪表权限和订阅后锁仪表行；新读数、规则状态、自动工单、来源关联和命令凭证在同一事务中保存。启用事件模块时，工单与仪表异常 Outbox 也在这次事务内写入。自动建单复用现有 `WorkOrderService.create`，没有另建一套工单业务。

判定使用严格 `<` / `>`；等于阈值视为恢复正常。状态按数据库接受新读数的顺序推进，不按客户端伪造的历史创建时间重排。每个触发器分别记录异常周期、首次异常时间、是否已经开单及历史开单次数。

| 新接受读数与已有状态 | `PER_EPISODE` 的结果 |
| --- | --- |
| 正常 → 异常 | 开始新周期；等待条件满足后开一张工单 |
| 异常 → 持续异常 | 同周期已开单则不重复；尚在等待则重新检查等待条件 |
| 异常 → 正常 | 结束当前异常状态，下一次异常可以开启新周期 |
| 正常 → 再次异常 | 新周期，可生成新的来源工单 |

### episodePolicy 与 recurrent

`episodePolicy` 是实际执行策略：

- `PER_EPISODE`：每个异常周期最多自动建单一次；规则不变时，必须出现新的正常读数，随后再次异常，才开启后续周期。
- `ONCE`：同一个触发器在新状态模型记录到一次开单后，不再自动开单。恢复正常、修改阈值也不清空已记录的总开单次数。

迁移把已有规则的 `episode_policy` 默认设为 `PER_EPISODE`，新规则也使用此默认值。旧 `recurrent` 字段继续接收和返回以兼容现有负载，但不再决定是否重复开单；不会把 `recurrent=false` 自动解释为 `ONCE`。旧界面的“重复”开关因此不能替代显式策略设置。当前 Web/移动端未新增策略选择控件，需在完整触发器 API 负载中显式设置 `episodePolicy`。更新未提供此字段时保留原策略。

条件、阈值、等待时长或策略的指纹变化，会在下一条新读数到来时重新开始该规则的异常评估；原工单来源保留旧指纹。`ONCE` 的历史开单次数不会随配置变化被重置。

### waitBefore 与读数频率

`waitBefore` 的单位是秒，必须非负；它表示从当前周期第一次被接受的异常读数开始，至少等待多久。**状态由新读数驱动，没有独立定时任务在等待到期时自动建单。**

例如 `waitBefore=60`：第一次异常读数开始计时；60 秒之后只有再接受一条仍异常的新读数，才会执行开单。如果中间接受了正常读数，则本周期结束，后续异常重新计时。未收到正常读数不等于证明物理设备在采样间隙一直异常。

原 `Meter.updateFrequency` 仍以天为单位，按公司时区与最新读数日期校验，最小为 1 天。实际开单时间可能受此频率限制晚于 `waitBefore`；本次未将仪表改造成高频遥测系统。

### 历史修正、迁移及来源

`PATCH /readings/{id}` 只修正历史数值，`DELETE` 只删除记录；两者都不重新开单、不回滚实时异常状态。即使删除最新读数，也由下一条新读数确定恢复或继续异常。一次已成功命令的重放返回当时的结果快照，不能当作历史记录的实时查询。

迁移不回放旧读数，也不为旧自动工单补建来源；部署后首次接受的新读数才建立触发器状态。部署到已有业务数据前，应核对正在处理的旧异常工单，不能把本次迁移视为历史重复工单的自动清理。

`meter_trigger_execution` 以 `(trigger_id, episode)` 唯一，关联触发读数、工单和规则指纹。删除读数/工单将来源外键置空；删除触发器/公司会级联删除其状态与来源。它用于追溯当前模型的生成关系，不是独立永久审计归档。

创建仪表触发器的现有商业许可证门禁继续生效。集成测试直接构造合成的既有规则以验证状态机，同时单独验证空许可证下生产创建入口仍拒绝；没有修改生产许可证判断。

## 4. 公司业务编号的并发修复

不同仪表可以同时触发同一公司的工单，因此只锁仪表不足以保护 `CustomSequence`。真实 Hibernate 测试先让两个持久化上下文缓存同一旧序列值，再并发执行两条读数命令，修复前确实产生两个工单但只有一个不同的 `customId`。

`CustomSequenceService` 现在先取得独立 namespace、按公司划分的 PostgreSQL 事务级 advisory lock；缺行时在锁内初始化，已有序列在锁后 `refresh(PESSIMISTIC_WRITE)`，所有 `getNext*` 分配都持锁到所在事务提交。数据读写仍通过 JPA repository。没有使用公司父行 `FOR UPDATE`，避免与业务已写入外键时取得的 `KEY SHARE` 锁形成升级冲突。

修复后的同一测试产生 `WO000001` 与 `WO000002`；另有并发首次使用测试验证只建立一条公司序列。该串行范围是同一公司所有业务序列分配，适合当前单体维修业务；不将它描述为无限吞吐量的编号服务，也不自动修复历史重复编号。

## 5. 代码与数据目录

| 位置 | 职责 |
| --- | --- |
| [InventoryCommandService](../api/src/main/java/com/grash/service/InventoryCommandService.java)、[PartService](../api/src/main/java/com/grash/service/PartService.java) | 命令总事务、锁顺序、delta、返料流水及库存副作用 |
| [DomainCommandStore](../api/src/main/java/com/grash/service/DomainCommandStore.java) | key 校验、事务级锁、负载绑定与结果重放 |
| [ReadingService](../api/src/main/java/com/grash/service/ReadingService.java)、[MeterTriggerCommandService](../api/src/main/java/com/grash/service/MeterTriggerCommandService.java)、[MeterEpisodeRules](../api/src/main/java/com/grash/service/MeterEpisodeRules.java) | 读数命令、持久化状态与纯业务判定 |
| [CustomSequenceService](../api/src/main/java/com/grash/service/CustomSequenceService.java) | 同公司并发编号分配 |
| [FreshMaintenanceActor](../api/src/main/java/com/grash/security/FreshMaintenanceActor.java) | 指定维护 HTTP 写入口的当前权限加载 |
| [领域迁移](../api/src/main/resources/db/changelog/2026-11-14-domain-reliability.xml) | `episode_policy`、`domain_command`、`meter_trigger_state`、`meter_trigger_execution`；由现有 master 引入 |
| [Web key 包装器](../frontend/src/utils/idempotentCommand.ts)、[移动端 key 包装器](../mobile/utils/idempotentCommand.ts) | 当前进程内的未知结果重试 key 复用 |

新命令表/状态表采用 JDBC 写入，外键按业务关系使用 `ON DELETE CASCADE` 或 `SET NULL`；原工单、读数、库存等实体保留 JPA。事件模块与只读查询模块分别维护自己的迁移及事务契约。

## 6. 验证证据与复现

2026-11-14 03:19:01 +08:00 完成的**选定组合回归为 585 项通过，0 失败、0 错误、0 跳过，BUILD SUCCESS**，不是全仓库全部测试。本机执行证据为 [final-tests.log](C:/Users/roxanne/Documents/Codex/work/cmms-enhancement/logs/final-tests.log)（本机日志，不纳入仓库）。

注意 Surefire 计数口径：本次 XML 中实际 `<testcase>` 合计为 585，与控制台一致；各 `<testsuite tests="...">` 属性相加却为 535。差额来自不同 `@Nested` 类的同名测试方法在聚合属性中合并计数：WorkOrder 41、Part 6、Notification 2、Schedule 1；对应的 50 个执行记录仍在 XML 中，不是漏跑或跳过。统计时使用完整 `classname + name` 或控制台最终汇总。

以下两个真实 PostgreSQL 测试类分别为 **11 项通过，0 失败、0 错误、0 跳过**。证据位于运行后生成的 `api/target/surefire-reports/com.grash.integration.*IntegrationTest.txt`；报告会被后续运行覆盖。

- [DomainReliabilityIntegrationTest](../api/src/test/java/com/grash/integration/DomainReliabilityIntegrationTest.java)：使用真实事务与锁，但部分 repository/EntityManager 为 JDBC 支撑的测试替身；覆盖命令重放、权限撤销、回滚、并发库存及异常周期等边界。该层单独通过不能证明 Hibernate 映射或完整 Spring 装配正确。
- [MaintenanceJpaIntegrationTest](../api/src/test/java/com/grash/integration/MaintenanceJpaIntegrationTest.java)：启动完整 Spring 应用，执行完整 Liquibase master 和 Hibernate `validate`，使用真实领域服务代理/JPA repository。11 项覆盖装配、库存重放/返料、真实 SQL 后回滚、并发扣库、异常恢复/读数重放/Outbox 原子提交、JPA 写入与查询 epoch、许可证门禁、真实通知表消费/权限撤销、旧序列缓存下不同仪表并发、安全序列初始化、旧明细缓存下批量返料。

其中，旧明细回归先缓存领用量 3，由另一事务把领用改成 5，再执行完整列表移除，最终验证归还 5、库存恢复 10。编号用例保留了“修复前 1 个不同编号、修复后 2 个不同编号”的真实失败与通过过程，而非仅检查是否调用了锁方法。

纯业务与入口回归还包括 `MeterEpisodeRulesTest`、`InventoryCommandServiceTest`、`InventoryFreshActorControllerTest`、`FreshMaintenanceActorTest` 以及现有 `PartServiceTest` / `PartControllerTest`。前端 key 行为由 `scripts/test-maintenance-client.cjs` 检查。组合运行和打包结果以执行日志为准，不能用某个 Mock 测试推断全部链路已经验证。

本地测试只允许专用隔离 PostgreSQL：`jdbc:postgresql://127.0.0.1:25432/cmms_enhancement_test`；Redis 为 26379，RabbitMQ 为 25672。服务配置见 [compose.enhancement-test.yml](../compose.enhancement-test.yml)。完整应用测试只替换邮件/存储/Webhook 等出站适配器，关闭自动事件 worker/listener 与外部投递，主动调用消费者；不会向真实下游发送消息。fixture 使用唯一合成公司，不清空共享表，保留合成记录便于排错。

从仓库根目录运行组合检查，原生本地 Rabbit 使用 guest；Compose/CI 使用脚本默认的专用测试账号：

```powershell
# 本机隔离服务已准备好时执行；Maven/JDK 及可选 Settings 使用本机安装路径。
powershell -File .\scripts\test-maintenance.ps1 -RabbitUser guest -RabbitPassword guest
node .\scripts\test-maintenance-client.cjs
```

仅运行完整 JPA 回归时，从 `api` 目录执行：

```powershell
mvn -B test "-Dtest=MaintenanceJpaIntegrationTest" `
  "-Dcmms.test.jdbc-url=jdbc:postgresql://127.0.0.1:25432/cmms_enhancement_test" `
  "-Dcmms.test.jdbc-user=cmms_test" "-Dcmms.test.jdbc-password=cmms_test_local" `
  "-Dcmms.test.rabbit-user=guest" "-Dcmms.test.rabbit-password=guest"
```

未提供 `cmms.test.jdbc-url` 时，专用基础设施测试会跳过；这样的结果不能作为真实 PostgreSQL 回归通过证据。上述凭据仅属于本地合成测试实例，禁止把 URL 改成原 CMMS 或真实业务数据库。
