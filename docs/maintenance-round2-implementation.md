# 第二轮增强实施与验收

本文只记录共享限流、可靠通知生命周期治理。保留 Atlas CMMS 的设备、仪表、工单、备件及维修统计业务；原有授权检查继续生效。

## 环境与当前状态

- 开发仓库：`E:\intern projects\cmms-fork`，分支 `feat/mysql-maintenance`。
- 未修改或重启 `E:\intern projects\cmms` 及原部署、数据库、本地 Redis/RabbitMQ。
- Windows 仅编译及运行纯单元测试。真实数据库、Redis、RabbitMQ、应用启动和故障注入都在 GitHub 临时 Linux runner 的独立容器执行；没有使用本机已有服务。
- 新功能默认关闭。通知清理同时要求 `enabled=true` 和 `dry-run=false` 才能执行，配置只在临时 CI Compose 中启用。
- R1 已验证版本：`4ddc978664287cc650add7e7002023b4c9e99a30`。
- R2 验收状态以本文后续“最终验证记录”和 CI 结果为准；未完成的检查不能视为通过。

## 一、Redis + Lua 共享限流

### 业务与实现

原有进程内 Bucket4j 预算不能跨多个 API 实例共享。新增共享准入控制覆盖登录、认证报修及公开报修入口，其余旧入口保留原行为。

- `api/src/main/java/com/grash/security/ratelimit/SharedMaintenanceRateLimiter.java`：入口识别、服务端公司/用户标识、HMAC 键、可信代理解析、Lua 执行、指标。
- `api/src/main/resources/redis/maintenance-rate-limit.lua`：短期及长期双令牌桶。Redis TIME 统一时间；一次检查公司和个人预算，全部满足后才扣减，拒绝不扣另一层额度；TTL 清理闲置状态。
- `RateLimitFilter.java`：共享路径通过后不重复消耗旧的通用进程内限额；后续认证、业务授权及登录防暴力检查仍执行。
- `RequestService.createFromPortal()`：先经原 `RequestPortalService.findByUuidByUser()` 的方案和许可证检查，才执行已解析公司的门户额度检查。
- 认证报修额度来自认证用户的公司和用户 ID；不接受请求体传入的租户。公开入口先按来源 IP 限流，已授权门户再按公司控制。
- 相同入口/公司键使用相同 Redis Cluster hash slot；用户/IP 使用 HMAC，密钥不进入日志。

### 配置与失败语义

```yaml
cmms:
  rate-limit:
    shared:
      enabled: false
      namespace: isolated-environment-name
      identity-secret: ${PRIVATE_RATE_LIMIT_SECRET}
      trusted-proxies: ''
      subject: {burst: 10, refill-seconds: 60, sustained: 60, sustained-seconds: 3600}
      organization: {burst: 120, refill-seconds: 60, sustained: 600, sustained-seconds: 3600}
      anonymous: {burst: 20, refill-seconds: 60, sustained: 100, sustained-seconds: 3600}
```

密钥至少 32 字符，所有实例共享同一私有密钥、命名空间和预算。命名空间按环境隔离。Redis 连接/命令超时需不超过两秒，测试环境使用 500ms。不要把示例密钥作为真实密钥。修改预算签名或密钥会重建额度，应通过统一发布控制。

共享限额耗尽返回 429 和 `Retry-After`；Redis 不可用返回 503，不回退到独立本地额度。失败仅阻断受保护写入/登录，读取资产等非目标路径继续服务。超时的 Lua 可能已消耗额度，客户端不得假设完全没有扣减。Lua 的原子执行不等于 Redis OOM 时具备数据库事务回滚；Redis 故障切换丢失状态也可能重置额度。

`trusted-proxies` 默认空，直接连接地址为准；不要配置全网可信，所有实例使用相同代理边界。`ClientIpResolver` 先校验数字 IP，避免把伪造地址作为 DNS 查询。限流不替代验证码、权限或许可证。

### 已有验证证据

[R1 容器运行](https://github.com/roxanne1707/cmms_1707/actions/runs/43562782395)：34 项显式容器测试及 25 项 Compose 检查通过，无跳过，临时资源清理成功。脱敏摘要见 `docs/evidence/round2-implementation/r1-*.json`。

- 两个真实 API 进程共享一套临时 PG/Redis：40 次报修、10 并发，12 次接受、28 次 429，与预设配额一致。
- 接受请求 p95/p99 约 3030.764ms；拒绝 p95 146.224ms、p99 169.749ms。样本少，包含完整业务处理，不能表述为 Lua 延迟或容量上限，也没有性能提升百分比。
- Redis 暂停时保护接口约 554.055ms 返回 503；恢复后额度仍受限。应用重启不重置预算，伪造 XFF 不绕过 IP 限额。
- Testcontainers 覆盖多实例原子扣减、租户/用户隔离、拒绝不误扣公司配额、IPv6 归一化、TTL、SCRIPT FLUSH、Redis 暂停及错误类型。
- 缺失门户未生成报修记录。未配置商业许可，未验证有效付费门户的完整成功流程，未绕过任何许可判断。

## 二、可靠通知生命周期治理

### 清理边界

原版及第一轮增强已经具有 Outbox、RabbitMQ 发布确认、消费幂等、重试、失败补投。第二轮个人增量是成功传输载荷的安全退役、摘要审计、保护条件、批次治理和告警规则，不能把原有消息机制重新计作新增成果。

`EventRetentionService` 按事件整组处理，默认保留 90 天，使用数据库时间和终态活动时间判断，不能仅按创建日期删除：

1. 已有消费凭据，且消费完成时间超过保留期。
2. 最新消费尝试为 DONE，没有 READY/PROCESSING，没有租约/令牌，所有尝试的最近变更均足够久。
3. 全部 Outbox 已发布且发布时间足够久，没有未发布或租约记录。
4. 全部投递为 SENT/SKIPPED，其最后终态时间足够久；任何 DEAD/RETRY/PROCESSING 都保护整个事件。
5. 没有报告附件。先由既有附件生命周期清理处理附件，再考虑传输载荷。
6. 尚无归档摘要，且整组行数和载荷字节数在配置预算内。

单事件在独立短事务内，使用与人工重放相同的数据库互斥键，再锁定整组行并复查条件；先插入 `event_archive_summary`，再删除该组 Outbox/重试行，把终态投递 payload 置为 `{}`。任一步骤失败整组回滚。不能对组内行使用 SKIP LOCKED 后继续部分删除。

**永久保留消费凭据**，保留投递元数据、重放审计、通知和设备维修业务历史。摘要保存事件类型、业务 ID、时间、数量、载荷字节数和 envelope SHA-256，不保存完整敏感载荷。它是载荷退役摘要，不是可恢复备份；无法从摘要重建邮件或重放已完成事件。

`EventStore.append()` 与清理共用互斥键：已完成事件不会重建载荷；`claimAttempt()` 先检查消费凭据，已清理事件的旧 RabbitMQ 消息安全 ACK。真正未知的消息继续隔离，不能一概忽略。

### API、数据库与调度

- `GET /reliable-events/retention`：当前公司最近 20 条批次及摘要，候选最多为单批上限，不是全库总量。
- `POST /reliable-events/retention/runs`：必须显式填写 `dryRun`、5–500 字原因。先 `dryRun=true` 预览；正式执行需双配置开关。预览仍写批次审计，但不修改事件数据。
- 重用 `EventReplayService.authorize()`，从数据库重新读取启用状态和管理员/公司所有者身份；公司 ID 来自认证用户，不允许客户端指定其他公司。
- `EventRetentionWorker` 默认每小时运行，支持多实例通过事件级互斥及摘要唯一键排除重复。无需引入另一个调度平台。
- `event_retention_batch` 记录 RUNNING/COMPLETED/PARTIAL/FAILED/ABANDONED、数量、逻辑退役字节、操作者和原因。中断超一小时的批次标为 ABANDONED；后续批次可继续尚未归档的事件。
- 新增 Liquibase 文件 `2026_11_14_event_retention.xml`，分别引用 PG/MySQL SQL；没有修改旧迁移内容，保留历史校验和。
- 新索引支持消费时间候选扫描、按公司事件查 Outbox、批次时间查询。实际查询计划和大规模扫描成本仍需专门测量。

```yaml
cmms:
  events:
    retention:
      enabled: false
      dry-run: true
      retain-days: 90
      batch-size: 20
      max-rows-per-event: 2000
      max-payload-bytes-per-event: 2097152
      budget-millis: 10000
      transaction-timeout-seconds: 5
      poll-ms: 3600000
      initial-delay-ms: 3600000
```

单事件超行数/字节预算会跳过，不强行截断。持续超预算候选可能占用批次，需要管理员根据脱敏批次、候选和数据库诊断调整预算；尚未实现自动隔离这些候选。批次预算不等于数据库查询的硬截止，不能宣称所有请求严格十秒内完成。

### 可观测性及处理流程

`EventRetentionMetrics` 提供开关、最近成功执行时间、候选样本、超期秒数；执行计数、失败计数、耗时和逻辑载荷字节通过 Micrometer 输出，避免公司/用户/事件高基数标签。

`observability/maintenance-alerts.yml` 定义 Outbox 超期、DEAD 投递、清理失败、候选持续超期、指标读取失败/应用失联规则。`maintenance-alerts.test.yml` 用 promtool 验证触发和恢复。现有监控启动脚本将加载规则，本轮未运行该脚本或重启本机监控。

告警处理：先检查应用/数据库及 RabbitMQ 健康，再检查失败批次；DEAD 通知修复配置后使用既有失败补投接口。不要为消除告警直接删除消费凭据或 DEAD 任务。没有配置 Alertmanager 和外部接收目标，规则存在不代表已向真实邮箱/设备发送告警。

### 容量与隐私限制

退役字节为逻辑载荷长度，不是磁盘回收量或数据库文件缩小量；PG VACUUM、MySQL 表空间维护需独立评估。本方案保留 receipt、摘要、投递元数据、批次、数据库互斥记录和失败数据，不能声称所有表增长均已受控。长期分区/凭据删除需要明确最大重投窗口及业务审计政策，暂不自动实施。

## 三、复现与验收

仅在全新 Docker 测试环境运行显式容器套件：

```sh
mvn -B -f api/pom.xml '-Dtest=RoleIntegrationTest,DeliveryProtocolContainerIT,SharedRateLimitContainerIT,EventRetentionContainerIT' test
docker run --rm --entrypoint /bin/promtool -v "$PWD/observability:/rules:ro" -w /rules prom/prometheus:v3.16.0 test rules maintenance-alerts.test.yml
```

Compose 验收脚本只允许 GitHub Linux runner，随机私有凭据、独立项目名、数据库端口不向宿主发布、无用户数据挂载、禁止外部邮件/Push/Webhook。结束仅删除该次临时项目及卷。

- 核心闭环：创建设备/工单→指派维修人员→开始维修→领用备件 10→8→重复领用不再扣→完成维修→统计台账→消息凭据→应用重启后记录仍在。
- R1：双 API 共享配额、额度精确、租户隔离、伪造 IP、Redis 故障及恢复、重启保留额度。
- R2：预览无事件写入、双开关、公司范围、普通维修人员拒绝、超期完成事件退役、保留凭据、重复执行为零。
- Testcontainers：两数据库分别覆盖失败/未完成/附件/活跃租约保护、并发清理只归档一次、删除受阻时整组回滚及重试恢复、预算上限、清理后迟到 RabbitMQ 消息 ACK、未知消息隔离。
- 本机已执行：新增代码编译及 54 项限流纯单元测试通过；另有 3 项清理安全测试通过；汇总工具 7 项自测通过。完整测试结果见最终验证记录。

### 核心源码定位

| 关注点 | 源码位置 |
|---|---|
| 共享准入与门户组织额度 | `api/src/main/java/com/grash/security/ratelimit/SharedMaintenanceRateLimiter.java:40`、`:60` |
| 已完成事件禁止重建载荷 | `api/src/main/java/com/grash/event/EventStore.java:27` |
| 消费凭据处理迟到消息 | `api/src/main/java/com/grash/event/EventStore.java:94` |
| 双开关、预算、批次与事件事务 | `api/src/main/java/com/grash/event/EventRetentionService.java:73` |
| 锁定整组、摘要写入及删除事务 | `api/src/main/java/com/grash/event/EventRetentionService.java:116` |
| 管理接口公司作用域 | `api/src/main/java/com/grash/event/EventRetentionController.java:25` |
| 重新加载管理员权限 | `api/src/main/java/com/grash/event/EventReplayService.java:79` |

### 提交拆分及测试修正

功能已经拆为共享限流、通知生命周期、容器端到端验收及安全告警测试等提交，均位于功能分支，未合并主分支或触发部署 workflow。

验证中修正了三类测试基础问题：Linux 文件时间精度使汇总自测误判陈旧文件；旧验收脚本把维修员也创建成管理员；旧 MySQL 窄测试夹具缺少新增互斥路径依赖的 `cmms_transaction_lock` 表。对应修复只调整测试夹具/工具，不降低权限条件、不跳过失败用例。真实维修闭环改为使用 TECHNICIAN；独立报修配额测试通过正常角色 API 切换为 REQUESTER，再重新登录，遵守各角色真实业务权限。

完整应用验收还发现 MySQL 新治理表未显式指定排序规则，而既有事件表使用 `utf8mb4_0900_as_cs`。数据库默认 `ai_ci` 时可能产生跨表比较不兼容。新 DDL 已显式对齐原表；Testcontainers 故意用 `ai_ci` 创建测试数据库，保证测试覆盖默认规则不同的部署条件。此变更只针对尚未向用户部署的本轮新迁移；所有临时容器已销毁，没有修改原项目历史迁移或用户数据库中的 Liquibase 记录。

## 四、面试可讲的个人增量

1. 面向维修报修入口的跨实例 Lua 原子配额，可信代理、租户身份、保守故障策略及可复现故障注入。
2. 消费凭据与传输载荷分离，清理和重放共用互斥边界，整组事务、约束、迟到消息及失败恢复测试。
3. PG/MySQL 两套真实事务验证、真实 RabbitMQ ACK，以及完整维修闭环验收。

面试必须能解释：为什么 Redis 超时返回 503、为什么两层预算要一次判断、凭据为什么不能直接按 TTL 删除、为什么归档摘要无法恢复重放、为什么不能跳过锁住的部分行、保留多少元数据仍有增长、原有至少一次消息处理与外部投递的重复边界。不能宣传端到端 exactly-once、高并发容量或未经基线测量的性能提升。

## 最终验证记录

最终功能代码：`51cb9fe4afa83b25753c477648cec08dd4e25e9f`。下列均对应此 commit；后续文档提交不改变已测业务代码。

| 验收项 | 结果 | 证据 |
|---|---|---|
| 显式真实容器套件 | 50 项，0 失败/错误/跳过 | [容器 CI](https://github.com/roxanne1707/cmms_1707/actions/runs/43568578500)，`r2-container-summary.json` |
| 告警表达式及触发/恢复 | promtool 全套通过 | 同上 testcontainers job |
| 实际 PG/MySQL 应用启动、维修闭环、重启、限流及清理接口 | 27 项检查通过，临时容器/卷清理成功 | 同上 compose-apps job，`r2-compose-summary.json` |
| PostgreSQL 全量后端回归 | 2188 项，0 失败/错误/跳过 | [全量 CI](https://github.com/roxanne1707/cmms_1707/actions/runs/43568578504)，`r2-postgresql-summary.json` |
| MySQL 全量后端回归 | 2188 项，0 失败/错误/跳过；可执行 JAR 启动检查通过 | 同上 MySQL job，`r2-mysql-summary.json` |
| 前端自动检查、TypeScript 检查及生产构建 | 51 项检查通过，类型检查与生产构建通过 | 同上 frontend job，`r2-frontend-summary.json` |

证据目录：`docs/evidence/round2-implementation/`。Maven 计数来自最终总计并与控制台各套件逐项核对；嵌套 JUnit XML 可能遗漏父级测试，不能直接把 XML 汇总 2088 当作全量 2188。

最终清理接口小样本：PG/MySQL 均归档 4 个超期事件、各保留 4 条凭据、重复归档 0；逻辑退役载荷分别 11095/11305 字节，接口单次耗时 136.235/112.026ms。该结果不证明大规模历史数据下的清理吞吐量；未做十万/百万条历史事件基准或生产磁盘回收测量。

验收清单：

- [x] 独立部署、核心维修闭环、库存幂等、消息消费与重启持久化。
- [x] 两真实 API 进程共享限额、Redis 故障及恢复、伪造代理头测试。
- [x] PG/MySQL 通知清理预览、执行、重跑、权限控制和保留凭据。
- [x] 两数据库并发清理、失败回滚/恢复、预算保护及 RabbitMQ 迟到消息。
- [x] 原部署、数据库和许可检查保持不动。
- [x] MySQL 最终全量回归及可执行 JAR 启动检查通过。

明确未验证/未承诺：真实收件邮箱、Push 设备、外部 Webhook/告警接收链路；有效商业许可门户成功流程；多节点 Redis/RabbitMQ 故障切换；百万级历史数据清理。未在现有部署启用这两个新功能，也未删除任何用户历史记录。
