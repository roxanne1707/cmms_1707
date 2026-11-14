# 维修通知与调度可靠性补全

> 状态入口：本文保留该阶段的源码证据和验收记录；当前实现、测试及启动方式以 [最终收尾验收](maintenance-final-acceptance.md) 为准。历史轮次的测试数、分支和运行地址不能直接当作当前状态。


本轮只修改 `cmms-fork`，仅在隔离 PostgreSQL、RabbitMQ 及本地 HTTP 模拟接收端验证。原 `cmms` 部署、数据库与配置未迁移。以下是本次增量范围；统一回归结果以最终验收清单为准，不把编写了测试等同于测试已通过。

## 已实现的业务变化

| 场景 | 原缺口 | 本次实现与源码 |
|---|---|---|
| 删除维修工单 | 删除邮件直接发送，Webhook 与删除无可靠提交保证 | `WorkOrderService.deleteByIdAndUser → delete → ReliableEventService.workOrderDeleted`。删除事务内写快照与 Outbox；原工单不存在时按当前同企业管理员权限校验，邮件与 Webhook 由投递账本处理。见 `api/src/main/java/com/grash/event/ReliableEventService.java:138`、`EventAccessPolicy.java:42` |
| 低库存通知 | 库存事务提交后回调尽力发送，回调失败无持久恢复 | `PartService.consumePart/restockPart/update` 的数量变更在原事务内产生 `PART_QUANTITY_CHANGED`；符合原低库存规则时加入去重收件人；开启可靠模式后不再调用相同旧邮件/站内通知分支。见 `ReliableEventService.java:154` |
| 工单报告邮件 | PDF 生成后直接调用邮件服务，失败缺少可重试快照 | PDF 生成完成后调用另一 Bean 的 `@Transactional workOrderReport`；附件与 Outbox 一起提交。10 MiB 单报告上限，附件按企业与 eventId 私有保存，MQ 不携带 PDF。见 `ReliableEventService.java:198`、`ReportAttachmentStore.java:20` |
| 多个报修关联同一工单 | 只通知 `parentRequest` 一个请求人 | 同企业查询所有关联报修，按报修 ID 去重；每个匿名目标携带自身 requestId，外发前再核对当前 request→workOrder 关系。见 `ReliableEventService.java:98`、`EventAccessPolicy.java:31` |
| 保养到期提醒 | Quartz Job 内直接发送邮件 | 独立命令事务生成 `PREVENTIVE_REMINDER`；用户与同企业管理员去重；实际发送时再次核对当前保养权限和偏好。见 `ReliableEventService.java:182` |
| 定期保养自动工单 | 仅依赖 Quartz 执行次数，无业务发生凭据 | `MaintenanceScheduleExecutor` 是真正的 Spring 事务边界，锁定并刷新 Schedule；按企业、计划、执行种类、计划发生时间唯一记录，开单/提醒与凭据同事务；失败全部回滚。见 `service/MaintenanceScheduleExecutor.java:35`、`MaintenanceScheduleReceipt.java:13` |

## 投递与数据保留契约

- `WORK_ORDER_DELETED` 不再尝试加载已删除工单，而检查目标用户仍启用、仍属于原企业、订阅席位有效且仍有 SETTINGS 权限。邮件再核对邮箱与邮件开关，避免“删除后所有事件都因找不到工单而丢弃”。
- `LOW_STOCK` 保留原许可条件；投递时再次检查 `LOW_STOCK_ALERTS` 以及当前备件可见性。持续低库存的**不同有效领用操作**仍可各产生通知；它不是新增的低库存异常周期抑制器。重复网络提交由已有库存命令幂等机制防止再次变更及发事件。
- 报告是发送申请时的 PDF 快照；发送前重新确认发起人的当前工单权限、当前客户归属和邮箱。已删除客户、已改邮箱、已撤权发起人不会继续收到该快照。
- PDF 保存在 `event_report_attachment`，没有公开下载接口或永久公共链接。SMTP/SendGrid 共用此快照；本地模拟投递会验证附件内容和稳定 `Idempotency-Key`，不访问真实邮箱。
- 默认每日清理创建超过 90 天且已消费、所有投递均为 SENT/SKIPPED、最新尝试无处理中或失败的报告附件。DEAD、重试中、尚未消费的附件保留以供补投。历史 DEAD 后已成功重放不会永久阻止清理。清理只针对新附件表；长期未解决的失败报告仍需运营处理并监测磁盘使用。
- 原报告页面发送成功仅代表申请已受理；异步投递是否 SENT 应查看可靠通知状态。SMTP 接受后崩溃仍可能重发，不声称邮件 exactly-once。

## Quartz 改造边界

1. 保留 JDBC JobStore，配置集群模式与自动实例 ID；每个现有 Job 增加 `@DisallowConcurrentExecution`，同 JobKey 在共享 Quartz 存储中避免重入。
2. 保养开单与提醒不依赖 `QuartzJobBean` 的 final `execute` 自调用拦截，而由另一个 Service 代理开启事务。
3. 幂等键使用 Quartz **scheduledFireTime**，不得用实际执行时的 `now()`，否则每次重试都会变成新命令。同一企业、计划、种类与计划时间只能提交一次。
4. 删除/重排计划时同时删除遗留 chained Job；集群中两个启动实例重复注册同一任务只忽略明确的 `ObjectAlreadyExistsException`，其他调度故障继续报告。
5. 周频判断使用计划工作日期；提前提醒会先加回提前天数，再判断是否属于应执行周。避免重试延迟或提醒提前跨周导致错误判断。
6. 保养Job失败后通过Quartz持久触发器最多重试3次，退避1/5/30分钟；每次JobData保存原发生时间，重试不产生新业务键。requestRecovery使用原计划触发时间（兼容Quartz字符串值），不以恢复时刻替代。上限耗尽记录Quartz/Sentry失败，需要排查后按原发生时间恢复，不无限立即重试。
7. 订阅到期任务在独立事务中锁定并重新读取订阅；已续期、已免费、Paddle 管理或没有到期日的不再重复降级。LDAP 失败向 Quartz 抛出异常并回滚，而非只打印后吞掉异常。

多实例必须共享同一 Quartz 存储与 schedulerName。业务凭据保护相同计划发生时间；它不把任意手动创建的新计划时间视为同一次执行，也不构成跨机器故障与网络分区已验收的证据。手动“立即执行保养”仍是独立用户操作。

## 数据库增量

`api/src/main/resources/db/changelog/2026_11_14_reliability_completion.xml` 追加两张表：

- `event_report_attachment`：企业/eventId 主键，私有 PDF 字节与文件名、创建时间，数据库大小约束。
- `maintenance_schedule_execution`：企业/计划/种类/计划发生时间主键，记录生成工单 ID 与时间。

未修改历史 Liquibase 校验和，也不删除原数据。业务失败与消息写入失败必须回滚同一个事务。

## 验收测试与复现范围

| 测试 | 证明什么 |
|---|---|
| `ExtraMaintenanceReliabilityIntegrationTest` | 真 PostgreSQL 随机 schema；并发发生一次业务效果/Outbox；故障后凭据/附件/Outbox 全回滚；企业附件隔离；完成态清理与失败态保留 |
| `MaintenanceScheduleExecutorTest` | 重复发生跳过、预提醒日期语义、开单失败传播、成功记录工单、缺失稳定时间拒绝 |
| 原 `WorkOrderCreationJobTest`、`PreventiveMaintenanceNotificationJobTest` | 原业务断言迁到新事务门面后仍覆盖缺失/禁用/周频、收件人去重、退订与模板变量 |
| `SubscriptionEndJobTest` | 除原行为外，旧触发不能覆盖续期结果、已免费不重复降级 |
| `ExtendedReliableEventServiceTest` | 删除快照、低库存收件人去重、报告附件先于事件、多个关联报修通知 |
| `ExtendedEventAccessPolicyTest` | 客户变更与发起人撤权、匿名报修归属、低库存许可/资源权限 |
| `ReportDeliverySenderTest` | 仅 loopback HTTP；私有 PDF 内容与稳定投递 ID；撤权时连附件也不加载 |
| `ReportAttachmentStoreTest` | 10 MiB 上限、空附件与危险文件名拒绝 |
| `PartServiceTest` 新回归 | 可靠分支写事件后不再调用原同步/回调通知渠道 |

所有数据库集成测试只在显式指定的 `cmms_enhancement_test` 上创建随机 schema，清理仅匹配自己创建的随机 schema。真正业务 JPA、迁移、应用启动及集群测试结果由主验收记录汇总；不把 receipt 层 JDBC 并发测试包装成全部 Hibernate 业务端到端证明。

## 全仓回归发现并修复的邮件语言缺陷

全仓测试首次暴露邮件模板在中文Windows环境下英文内容错误的20项断言失败。保留全部原模板断言，修复生产行为：

- `EmailConfiguration.emailMessageSource` 禁止回退到宿主系统语言。英文在基础 `mailMessages.properties` 中，没有 `_en` 文件；原默认策略会先选宿主的中文文件。
- `Helper.getLocale(Language.EN)` 明确返回 `Locale.ENGLISH`，不再使用服务器 `Locale.getDefault()`。
- `AbstractTemplateTest` 使用生产邮件MessageSource配置；新增 `EmailMessageLocaleTest` 检查英文、中文、法文及未知locale。
- 独立JVM复现日志 `C:\Users\roxanne\Documents\Codex\work\cmms-reliability-completion\locale-defect-proof.log`：中文宿主上默认策略的 English-preserved 为 false，禁止宿主回退后为 true。该JVM内的locale更改不影响其他进程或原部署。

新增 `MaintenanceJobRetryTest` 检查原时间、持久延迟重试、恢复时间、最大次数与并发注册冲突。最终重新编译后的全套结果由统一验收记录给出。