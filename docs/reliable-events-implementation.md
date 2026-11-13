# 可靠维修事件模块

> **前端联调补记（2026-11-14）**：已提供 `/app/maintenance/notification-failures` 页面。失败事件查询现只返回最新尝试仍 DEAD 且尚无消费凭据的事件，避免成功重放后的历史失败继续显示。新增对应数据库回归后，消息集成类本轮为 11 项通过；详情见 [前端交付补记](maintenance-enhancement-implementation.md#7-三个前端页面及真实接口联调2026-11-14)。

事件模块默认关闭（`cmms.events.enabled=false`）。开启后，工单创建、新增分派、状态变化和仪表异常周期通知通过 PostgreSQL Outbox 与 RabbitMQ 处理；关闭时继续使用原有通知路径。工单删除通知、手动发送报告邮件和低库存通知不在本次已迁移的事件范围内。

## 运行配置

```yaml
cmms:
  events:
    enabled: true
    workers-enabled: true
    listener-auto-startup: true
    exchange: cmms.work-order.events
    queue: cmms.notification.dispatch
    dead-exchange: cmms.work-order.dead
    dead-queue: cmms.notification.dead
    batch-size: 20
    max-attempts: 5
    lease-seconds: 60
    confirm-timeout-seconds: 10
    network-timeout-seconds: 10
    database-backoff-seconds: 10
    external-delivery-enabled: false
    local-http-sink: ""  # 仅供测试：必须是字面量回环地址，如 http://127.0.0.1:19091/deliver
spring:
  rabbitmq:
    host: 127.0.0.1
    port: 25672
```

完整应用的事务测试可设置 `cmms.events.workers-enabled=false` 和 `cmms.events.listener-auto-startup=false`，同时保持事件写入开启。测试随后显式调用任务领取和同步消费服务，避免后台线程抢占任务、干扰事务断言。

RabbitMQ 凭据沿用现有环境配置与密钥管理方式。SMTP、SendGrid 沿用现有邮件配置和模板。`mail.enable=false` 时，邮件投递记为 `SKIPPED`。配置本地测试接收端后，外部投递会转到本地模拟服务，但仍检查收件人权限、企业归属和许可证。未配置测试接收端且 `external-delivery-enabled=false` 时，外部任务记为 `SKIPPED`。是否开启真实外部投递由部署配置决定，自动化测试不会开启真实外发。

增量迁移文件为 `api/src/main/resources/db/changelog/2026_11_14_reliable_events.xml`。它新增持久化发布意图、消费尝试、成功回执、投递任务、重放审计和异常消息隔离表，并为 `notification` 增加可为空的事件标识与通知用途字段。原有通知记录仍可读取。该迁移使用 PostgreSQL 语法，不声明兼容 MySQL。

## 事务与恢复边界

- 业务事务同时写入原始事件、消费尝试和发布意图；事件写入失败会使业务变更一起回滚。
- 发布器使用 `FOR UPDATE SKIP LOCKED` 领取有限批次，提交租约后才在事务外发布消息。只有关联发布确认成功、且没有收到 `mandatory return` 时，才标记为 `PUBLISHED`。确认丢失可能导致重复发布。
- 监听器先领取持久化的消费尝试，再同步调用独立的 `NotificationCommandService` 事务。该事务锁定尝试记录、检查领取令牌、写入成功回执、按收件人和用途去重的站内通知、各目的地的投递任务，并标记 `DONE`。只有事务代理正常返回、提交完成后，监听器才 ACK。
- 消费事务失败时，上述业务效果全部回滚。新的事务负责持久化重试接管、下一次尝试及发布意图，或者写入 `DEAD` 和死信发布意图。完成可靠接管后才允许确认原消息。数据库不可用时暂停监听器，恢复前先探测数据库。
- 旧尝试不能绕过退避时间；旧领取令牌不能提交新持有者的状态。租约过期的 `PROCESSING` 尝试会被持久化恢复并重新发布。RabbitMQ 消息仅携带引用标识，实际事件内容保存在 PostgreSQL 中。
- 外部投递工作线程每次领取一个任务，先提交，再执行有超时限制的网络调用，最后通过领取令牌条件更新结果。网络发送成功、数据库结果写入失败时，外部请求仍可能重复。每项 Webhook 投递使用稳定标识，接收方可据此去重。SMTP 接受邮件不等于用户已收到，也不构成端到端恰好一次处理保证。
- WebSocket 仅作为事务提交后的提醒；站内通知列表是权威记录。客户端可通过刷新补回错过的提醒。

生成通知和实际投递时，会重新检查当前账号启用状态、订阅席位、资源访问权限、仪表订阅功能，以及 Webhook 许可和订阅功能。收件人候选与不可变内容在事件发生时确定，后续重新分派不会改写历史通知对象。通知用途区分同一用户的分派、完成和报修状态通知。Webhook 入队后，如果端点关闭了资源序列化选项，发送时也会停止附带已入队的资源快照。

## 管理员恢复接口

接口会重新加载当前账号并确认其已启用、订阅席位有效，且为本企业所有者或 `ADMIN` 角色。接口不接受客户端指定企业 ID；须使用已认证会话，查询和恢复操作均限定在该会话所属企业。

- `GET /reliable-events/failures`：最多返回 100 条终止失败事件及 100 条 `DEAD`／`SKIPPED` 投递记录，不返回原始载荷或端点密钥。
- `POST /reliable-events/{eventId}/replay`，请求体为 `{"reason":"已修正投递配置"}`：仅允许重放最新尝试为 `DEAD`、且没有成功消费回执的事件。保留原 `eventId`，开启新一代重放。
- `POST /reliable-events/deliveries/{deliveryId}/retry`，使用相同格式的请求体：仅重试指定的 `DEAD` 投递。保留 `deliveryId`，重置该任务的尝试预算并记录审计。此操作不会删除成功消费回执，也不会重新生成站内通知；不能通过此接口强制重发 `SENT` 或 `SKIPPED` 任务。

当前没有启用自动删除记录。保留成功回执是防止重复业务效果的前提；只要仍允许重放对应事件，运维人员就不能先清除这些去重回执。

## 测试与本轮实测结果

2026-11-14 本轮最终组合回归实际执行 **585 项测试，失败 0、错误 0、跳过 0**，打包成功。这是本次选定组合的结果，不代表仓库所有测试均已执行。完整 Spring 应用启动、真实完整表结构迁移，以及原表结构上的通知消费链也已验证通过。

本模块包含的 **18 项新增测试全部通过，且无跳过**：

- `WorkOrderEventListenerTest`：3 项，验证持久接管失败时不 ACK、提交后 ACK 的顺序，以及无法解析消息的可靠隔离。
- `EventAccessPolicyTest`：3 项，验证当前订阅席位、仪表功能、Webhook 许可证与订阅功能撤销后的拒绝行为。
- `EventDeliverySenderTest`：2 项，使用本地 HTTP 接收端验证 HMAC 签名、稳定投递标识、原有 Webhook 资源序列化字段，以及拒绝非回环地址的测试接收端。
- `ReliableEventsPostgresIntegrationTest`：10 项，使用真实 PostgreSQL、RabbitMQ 和本地 HTTP 模拟服务验证事务及消息恢复边界。

真实中间件集成测试仅在显式提供隔离数据库地址时运行：主机必须是字面量 `127.0.0.1`，数据库名称必须为 `cmms_enhancement_test`。每项测试创建并清理独立的 `events_it_*` schema，在最小原表夹具上执行实际事件迁移，并使用独立的 RabbitMQ 队列与交换机名称。

已覆盖的真实集成场景包括：业务回滚、重复消费、两个独立线程和数据库连接竞争同一消费尝试、过期领取令牌、有界持久重试、消费租约过期恢复、不支持的持久事件结构转死信、死信重放、跨企业恢复被拒、单个外部投递独立重试、本地 HTTP 503→200、关联发布确认、不可路由返回及手动 ACK。完整应用启动与迁移、现有服务回归由单独的组合测试覆盖。

```text
-Dtest=WorkOrderEventListenerTest,EventAccessPolicyTest,EventDeliverySenderTest,ReliableEventsPostgresIntegrationTest
-Dcmms.test.jdbc-url=jdbc:postgresql://127.0.0.1:25432/cmms_enhancement_test
-Dcmms.test.jdbc-user=<隔离测试用户名>
-Dcmms.test.jdbc-password=<隔离测试密码>
-Dcmms.test.rabbit-port=25672
```

测试没有发送真实邮件、移动推送或互联网 Webhook。单个本地 quorum 队列节点用于验证消息协议与持久任务行为，不能据此宣称已经验证高可用部署或真实生产故障恢复。当前指标包括 Outbox 待发布数量和最老任务年龄、消费 `DEAD` 数量、投递 `DEAD`／待处理数量及隔离消息数量；不会把用户 ID 或事件 ID 作为指标标签。
