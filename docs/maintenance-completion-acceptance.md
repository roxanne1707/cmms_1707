# CMMS 维修增强：完成情况与统一验收

日期：2026-11-14。开发目录：`cmms-fork`；分支：`feat/maintenance-reliability`。原始审计中的基线证据保留，本文件记录后续实现和最终测试结果。

## 1. 结论与验证边界

本轮完成核心增强代码、统一后端回归、前端构建与交互验收、性能对照、双节点定期保养恢复和本地监控验证。**后端完整回归 2,166 项：0 失败、0 错误、0 跳过**。这包含原项目测试和新增测试，不能把全部数量当作个人新增测试。

本轮未连接或迁移原部署数据库，未停止原部署服务；开发和测试均在 Fork 与独立运行环境。未发起真实支付、邮件、Push 或 Webhook；未更改商业许可证或绕过功能限制。代码尚未提交或推送，原报告、当前改动和本地运行配置均保留。

**尚未执行的环境级验收**：本机无 Docker，故 Compose/Testcontainers 容器路径没有实际运行；GitHub Actions 定义已更新，但未推送并执行远端 CI。不能将原生依赖测试称为容器测试，不能将 CI 配置称为 CI 通过。

## 2. 完成矩阵

| 模块 | 本轮最终状态及具体能力 | 验证依据 |
|---|---|---|
| 仪表异常周期 T01 | 保留 PER_EPISODE/ONCE、恢复与再次异常、锁与同事务创建、命令幂等 | DomainReliabilityIntegrationTest、MaintenanceJpaIntegrationTest；见领域可靠性文档 |
| 备件事务 | 领用/退料/补库/采购审批、非负库存、流水与幂等；低库存通知改为事务内可靠事件 | InventoryCommandServiceTest、真实 PostgreSQL 集成测试 |
| Redis 设备概览 | 权限先于缓存、数据库 epoch 版本键、TTL 抖动、Lua 释放锁、并发回源保护和降级 | QueryInfrastructureIntegrationTest；最终 API 对照与结果校验 |
| MyBatis-Plus 维修台账 | 新只读查询、筛选和分页；JPA 业务写模型保留 | 查询测试；页面真实分页、筛选、空状态、403、503 恢复 |
| RabbitMQ 可靠维修事件 | Outbox、confirm/return、持久化消费幂等、持久重试、死信/隔离、租约恢复和管理员补投 | ReliableEventsPostgresIntegrationTest 等；真实 RabbitMQ；最终页面与数据库审计复核 |
| 通知可靠性补齐 | 工单删除、低库存、预防性维护提醒、报告邮件进入可靠事件/投递账本；报告附件限额与安全清理 | ExtraMaintenanceReliabilityIntegrationTest、报告存储/发送测试 |
| 统计正确性与权限 | 移除 39 处会绕过方法授权的统计响应缓存；新增每次请求的真实用户/权限校验；修复部分时长、合规率和空值计算 | AnalyticsCorrectnessTest；原统计回归同时执行 |
| 多设备会话 | 会话列表、单会话/当前设备/全部设备撤销、刷新令牌家族及即时 access JWT 撤销；旧退出接口兼容 | 会话 PostgreSQL/安全测试；7 组真实 HTTP、6 组浏览器操作 |
| 实时连接 | 令牌续期、有限重连、同账号重新登录区分、新旧账号晚到消息隔离、取消/超时；不自动重复写命令 | 12 项 session socket 测试、25 项客户端幂等/刷新测试 |
| 重复报修关联 | 多条报修关联同设备已有工单；企业隔离、合法状态、同目标重放、冲突与关联审计 | 14 项 SLA/报修 PostgreSQL 测试；真实创建和页面关联两条报修 |
| SLA 超时升级 | 优先级策略、期限快照、显式纳管历史工单、首次响应/修复、Quartz 扫描和可靠升级通知 | 真实 Quartz 生成 2 条升级、2 条 Outbox、2 条消费凭据、2 条管理员站内通知 |
| 定期保养多实例 | Quartz 集群设置；原调度时间作为业务键；执行凭据、工单与事件同事务；有界持久重试及恢复 | 两节点正常触发及执行中停止节点后恢复，均为 1 凭据/1 工单/1 创建事件 |
| 监控 | 独立认证 Actuator/Prometheus、缓存/请求/堆/连接/消息指标，Grafana 看板及本机启动脚本 | 未认证401、认证200、Prometheus target up、查询结果与浏览器截图 |
| 独立 MySQL 学习实验 | 条件扣库存、乐观锁、RC/RR 可见性、回滚、复合索引 EXPLAIN | 原生独立 MySQL 8.0.33 / 23306 的 6 项实际实验；CMMS 主库仍为 PostgreSQL |

新增/增强页面：`/app/maintenance/overview`、`/ledger`、`/notification-failures`、`/sla`、`/request-links`，以及 `/app/account/sessions`（前三条简写均承接 `/app/maintenance`）。SLA/报修页补齐文档标题及主标题；公共顶栏对手机长标题进行截断，避免挤压菜单按钮。

## 3. 本轮回归实际发现并修复的问题

1. **报修同事务刷新覆盖**：审批后再次进入命令时，锁定后的 `EntityManager.refresh()` 可能丢弃尚未 flush 的工单关联。`RequestService.lockRequest()` 先同步事务内修改，再加锁刷新。保留原“重复审批应拒绝”“已审批不能取消”的断言，两项失败修复后完整重跑通过。
2. **系统语言污染邮件模板**：英文用户原来可能继承 Windows 系统默认语言。固定 EN 的语言映射，并关闭 MessageSource 的系统区域回退；保留模板断言。
3. **完整回归的隔离问题**：运行中的测试 API 曾与测试消费者争抢消息；不同 Spring 上下文复用同一数据库 schema 又导致初始化用户冲突。完整 Spring 集成测试改为各上下文独立 schema、停止后台调度/外发，并限制上下文缓存及并行度。隔离服务账号也已明确。
4. **主机内存不足**：并行前端构建和完整后端测试曾导致 JVM 原生内存失败。这轮失败没有计为通过；最终按串行构建/回归执行，完整 2,166 项通过。
5. **预览代理断连**：会话退出/导航导致的真实 ECONNRESET 曾使本地预览 Node 进程退出；补齐每条连接和代理套接字的错误处理，不吞掉进程级未知异常。50次真实连接重置后预览仍返回200。匿名故障注入之后的密集验收触发了正常限流，原失败证据保留；恢复独立测试服务基线后另做功能回归，限流配置始终保持默认，未将关闭限流后的结果冒充日常验收。
6. **页面与运行脚本**：修复 SLA/报修页面缺少浏览器标题、手机顶栏长标题挤压、监控数据目录未初始化。最终构建及相关页面重验收。

失败日志保留在本机工作证据目录；未通过删除断言、跳过测试或把模拟结果改成成功来处理失败。

## 4. 性能证据：有收益，也有不适合缓存的情况

环境：同一 Windows 主机（i5-11400H、约16GB内存）、Java17、两个独立 API 进程、同一 PostgreSQL16.16/Redis 测试依赖。使用合成的10万条工单；热点设备5万条、稀疏设备100条。每组5个虚拟用户、每次请求后等待50ms；3秒预热、15秒计量，交替顺序各重复3轮。认证和数据权限始终开启；只在性能模式关闭测试账号的HTTP配额，页面验收前恢复默认限流。

下表为**三次运行各自 p95 的中位数**，不是合并所有样本后的 p95。

| 查询 | 直接 SQL | Redis | 结论 |
|---|---:|---:|---|
| 热点设备5万条工单 | 56.307ms | 10.332ms | 本测试负载下有明显延迟收益 |
| 稀疏设备100条工单 | 9.121ms | 9.805ms | 没有证明缓存更快，不应推广收益到所有查询 |

12组计量共13,615次请求，结果校验全部通过，错误率0。缓存和直接查询的聚合结果一致。原始逐轮平均值、p95、p99、请求数见 [性能证据](evidence/maintenance-completion/api-performance-results.json)。固定用户数与等待时间下的请求率**不是系统峰值吞吐量**；短时同机合成负载也不是生产容量保证。

SQL 单独实验：10万条均匀合成数据，同一聚合查询比较企业单列索引与“企业+设备+创建时间+ID”复合索引；1轮预热、3轮计量，EXPLAIN ANALYZE 执行时间中位数 **3.881ms → 0.499ms**，结果一致。见 [执行计划](evidence/maintenance-completion/sql-performance-results.json)。这是特定数据分布的 SQL 实验，不能宣称全站同幅度提升。

性能数据随后事务性复制到独立测试库的 `cmms_perf_archive.work_order`，核对10万行后移出活跃测试表；此前30条演示工单保留，后续只通过真实接口追加新的验收工单。没有通过修改许可证或扩大限制来创建验收数据。

## 5. 可靠性与安全的验收语义

- **数据库业务幂等**：同一仪表异常周期、库存命令、调度发生时间、事件消费使用各自稳定业务键；不是笼统声称系统全局 exactly-once。
- **Quartz 故障试验**：只锁住本轮新建的合成保养计划，观察真实执行节点，核对 PID/项目路径/profile/端口后停止该测试 JVM，释放锁，由另一节点恢复。最后复核只有1条执行凭据、1张工单、1条创建事件。首次测试工具因 Windows 脚本策略未执行停止，不计入成功；修正工具启动方式后才完成真实停止试验，没有修改系统执行策略。
- **SLA**：自然时间；暂停不停表；只记录首次响应/首次修复，重新打开不重置该周期。策略变更不追改已纳管期限。历史工单须显式纳管，按原创建时间计算，可能立即超时。升级是通知维护管理员，不自动修改工单优先级。
- **通知补投**：最终页面试验中，重放后只有1条消费凭据、1条站内通知和1条审计；邮件补投因为外发关闭变为 `SKIPPED`，没有 `sent_at`。不能把“安排处理”或 `SKIPPED` 描述为邮件已送达。
- **外部系统**：SMTP 已接受但本地记账前崩溃仍可能重复发送；Webhook 接收方应按稳定投递ID去重。未做真实外部邮件/Push/Webhook 验收；单机 RabbitMQ 的测试不等于跨主机高可用证明。
- **会话**：即时撤销依赖数据库状态检查，牺牲纯无状态 JWT。客户端显示的信息由浏览器提供，不能作为物理设备身份鉴定。旧客户端退出保留兼容语义。
- **覆盖范围**：新可靠链路覆盖选定维修事件与本轮补齐的删除、报告、低库存和保养提醒；并未把全产品所有注册、邀请、旧报修通知及其他历史异步调用统一迁移。旧统计缓存权限与指定公式已修复，不声称全部历史统计 SQL 都已重新定义、优化或压测。

## 6. 入口、复现及维护

- 测试前端：`http://127.0.0.1:3289`；后端28089。28090仅作性能对照/双节点试验，最终停止以节省资源。
- 测试依赖：PostgreSQL25432、Redis26379、RabbitMQ25672。独立MySQL实验23306；Prometheus32909；Grafana33090。原部署3089/18089/15432没有切换到新代码。
- 测试账号：本地忽略文件 `local-runtime/maintenance-frontend/access.txt`。Grafana账号在 `local-runtime/maintenance-observability/access.json`。不要提交这些文件。
- 后端全量：`scripts/test-maintenance.ps1`，必要时传入 Maven/Settings。该脚本只允许指定的回环测试库。全新服务需先准备 PostgreSQL `unaccent` 扩展；CI步骤已加入相同初始化。
- 前端：`tsc --noEmit --incremental false`、`react-app-rewired build`、`node ../scripts/test-maintenance-client.cjs`、`node ../scripts/test-session-socket.cjs`。先完成前端构建，再做后端完整回归，避免本机内存争抢。
- 测试 API：`scripts/start-maintenance-lab.ps1 -EnableScheduling`；停止使用配套脚本核对进程身份。`-PerformanceMode` 仅用于本机合成压测，日常验收不启用。测试 profile 禁止真实外发；本地 Quartz 默认不启动，必须显式开启。
- 监控：下载并校验官方便携工具后执行 `python scripts/start-maintenance-observability.py --tools-root <工具目录>`；只监听回环地址，凭据/SQLite/时序数据均在忽略目录。详情见 [监控说明](../observability/README.md)。
- 证据摘要：[测试与源码摘要](evidence/maintenance-completion/completion-summary.json)、[双节点恢复](evidence/maintenance-completion/quartz-cluster-results.json)、[SLA数据库复核](evidence/maintenance-completion/sla-request-database.json)、[MySQL实验](evidence/maintenance-completion/mysql-experiment-results.json)、[监控](evidence/maintenance-completion/observability-results.json)。本机完整日志/截图保留在 `C:\Users\roxanne\Documents\Codex\work\cmms-completion` 及相关 session/frontend 工作证据目录。

完整后端回归针对实际 PostgreSQL、Redis、RabbitMQ 原生进程，测试源码保留 Testcontainers 路径。隔离 Compose 和 CI 的定义需要在具备 Docker 的环境/远端流水线再执行；当前不打“通过”勾。

## 7. 原项目能力与个人贡献边界

原项目已有：设备、工单、报修、保养、备件采购、JPA/PostgreSQL、Spring Security/JWT、部分缓存/限流/异步通知、Quartz、部署和测试基础。不能把这些作为自己从零实现。

二次开发的可展示增量：异常周期状态机及幂等、库存事务流水、版本化设备概览缓存、MyBatis-Plus新维修查询、Outbox/RabbitMQ可靠事件、管理员恢复工作台、SLA快照与升级、重复报修关联、会话撤销、相关自动化测试和实测证据。

本轮代码由 Codex 协助完成。只有你能独立复现失败、解释不变量与锁顺序、理解缓存失效和消息故障窗口，并能修改测试验证时，才应表述为自己掌握的能力。保留原作者归属及AGPL/商业许可要求；不将原作者代码和性能数据包装为原创。

简历可据实表述：“基于 Atlas CMMS 开源设备维护系统进行二次开发，在 PostgreSQL 事务中实现仪表异常周期工单与备件命令幂等，结合 Redis 版本化缓存和 Outbox/RabbitMQ 构建可靠维修通知；完成重复报修关联、SLA及故障恢复验证。”具体性能数字必须同时说明数据量、负载、指标和测试环境。
