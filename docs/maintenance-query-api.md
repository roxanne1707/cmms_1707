# 设备工单概览与维修台账 API

> 状态入口：本文保留该阶段的源码证据和验收记录；当前实现、测试及启动方式以 [最终收尾验收](maintenance-final-acceptance.md) 为准。历史轮次的测试数、分支和运行地址不能直接当作当前状态。


## 启用与边界

新增接口仅在 `enhancement.query.enabled=true` 时注册，默认关闭。先完成主 Liquibase 迁移；查询模块依赖 `asset_summary_epoch`、版本序列/触发器，以及 C2 的 `meter_trigger_execution` 来源表。旧分析接口、JPA 写入和既有认证过滤器保持原路径。

`enhancement.query.cache.enabled` 默认为 `true`。设为 `false` 可运行同一 HTTP 接口的无缓存 JPA 聚合对照：仍执行相同的新鲜授权、设备检查和 PG 版本查询，避免把删掉权限检查带来的时间差冒充缓存收益。这个开关用于重启隔离实例后的对照，不是客户端可绕过的参数。

| 接口 | 请求体 |
|---|---|
| `POST /query/assets/{assetId}/work-order-overview?companyId=...` | 创建时间区间及归档/演示筛选 |
| `POST /query/maintenance-ledger?companyId=...` | 相同区间，加设备、状态、优先级、仪表触发来源和分页 |

`companyId` 可省略，默认当前企业；指定值须由当前企业关系授权。缓存前在独立 EntityManager 中重新加载账号、角色、订阅及企业关系，禁用 Hibernate 缓存读取；不以缓存 User 的权限作决定。所有新接口要求启用账号/订阅、ROLE_CLIENT、ANALYTICS 权限及订阅特性、WORK_ORDERS 查看和查看他人工单权限。概览还要求当前设备可见；台账要求 ASSETS 查看和查看他人设备权限，因为它会列出所有匹配设备名称。首版没有分配人/团队级统计。

## 参数和指标契约

`start`、`end` 为必填 ISO-8601 时间，统一到毫秒精度后同时用于 SQL 和缓存键。区间为 `[start,end)`，最长 366 天，日期限定在 1900–2200 年。现有不带时区的 SQL timestamp 按 UTC 解释；历史数据若使用其他时区，须先核对再启用。

`includeArchived=true` 为默认值，保留维修历史；`includeDemo=false` 默认排除演示工单。两个接口采用相同规则。概览以 `created_at` 选择创建期 cohort：

| 字段 | 含义 |
|---|---|
| total / complete | cohort 工单总数 / 当前 COMPLETE 数量 |
| completionRate | complete / total，范围 0–1；总数为 0 时 null |
| averageCycleSeconds | 有效已完成样本的平均 `completed_on-created_at` 秒数；空样本 null |
| validCycleSamples / invalidCycleSamples | 有效周期样本 / COMPLETE 但完成时间缺失或早于创建时间的样本 |
| metricVersion / epoch / generatedAt | 指标口径版本、数据库汇总版本、聚合开始时间 |

完成时间缺失或负时长的 COMPLETE 工单仍计入 complete，但不计入平均周期。正向的未来完成时间按记录值纳入，不使用动态 `now()` 过滤；因此没有“时间到了、版本未变、样本却变化”的隐含依赖。旧导入路径的时间质量需要单独审计。这是工单周期，不是 MTTR：不含报修等待，不扣除暂停。

台账可选字段：

- `assetId`：指定设备；不指定时保留没有关联设备的工单，其设备字段为 null。
- `status`：OPEN / IN_PROGRESS / ON_HOLD / COMPLETE。
- `priority`：NONE / LOW / MEDIUM / HIGH。
- `meterTriggered`：按 C2 异常周期生成来源筛选；仅返回来源布尔标记，不返回仪表读数、规则或其他仪表细节。
- `page`：从 0 开始，最大 10000；`size`：1–100，默认 25。
- `sort`：CREATED_DESC（默认）或 CREATED_ASC；同一创建时间按 ID 同方向排序。

状态/优先级按旧库整数值显式映射，不依赖 MP 默认 Enum handler。来源使用同时限定企业和工单的 EXISTS；设备 LEFT JOIN 也限定企业，跨企业设备名称不会出现在结果中。数据与独立 count 在同一个只读 REPEATABLE_READ 事务内获取。客户端排序字段不直接拼接 SQL。

## 请求示例

下面使用隔离实例及占位变量。认证仍使用原有 JWT 或已获许可的 API Key；不要将真实令牌写入仓库。

保存 `overview-request.json`：

```json
{
  "start": "2026-01-01T00:00:00Z",
  "end": "2026-02-01T00:00:00Z",
  "includeArchived": true,
  "includeDemo": false
}
```

PowerShell 中使用已经设置的 `LAB_TOKEN` 环境变量：

```powershell
curl.exe -X POST "http://127.0.0.1:28089/query/assets/101/work-order-overview?companyId=11" -H "Authorization: Bearer $env:LAB_TOKEN" -H "Content-Type: application/json" --data-binary "@overview-request.json"
```

台账请求体可在上述区间中增加 `"status":"COMPLETE","meterTriggered":true,"page":0,"size":25`，请求 `POST /query/maintenance-ledger`。返回结构为 `{content:[...],total,page,size}`。

日期、数字或 Enum 输入错误返回 400；当前权限不足返回 403；设备不存在或不在可见范围返回 404。回源容量耗尽返回 503。Redis 连接失败本身不会拒绝已授权且 PG 可处理的查询。

## 缓存一致性与故障

缓存键含指标版本、company/asset、PG epoch、服务端用户范围、精确规范化时间及归档/演示条件。缓存只保存不可变 JSON DTO。

- 工单 INSERT/相关 UPDATE/DELETE 都由 PG 行触发器同事务推进 epoch；包括批量/native 写入和设备改绑两侧。只修改标题不会改变此指标版本。
- TRUNCATE 不调用行 DELETE 触发器，因此另有语句触发器推进全部设备版本。
- epoch 由独立 PG sequence 产生，并在冲突行锁后求新值。设备移公司往返、删除后重建不会复用旧 key。
- 回滚恢复 epoch 行，sequence 留出空号是预期行为。不要把“连续编号”当一致性要求。
- 写提交后才开始的新请求从主 PG 取得新 epoch；已经在途的请求允许返回旧视图。不能把 epoch 读路由到延迟副本或另行缓存。
- 旧版本 Redis key 由 TTL 回收，不扫描 KEYS。该版本不能用于宣称旧 MTBF、停机或成本概览也同步一致。

每实例最多 256 个 single-flight key、8 个并行 PG 重建任务。Redis `SET NX PX` 短租约只抑制重复计算；Lua 比较 token 后释放，旧持有者不能删新锁。锁失效不影响版本键正确性，也不保证库存正确性。Redis 错误回源 PG，等待和回源并发有上限。

TTL 为随机 30–60 秒，扣除聚合执行时间；长查询不能回填后再取得完整新 TTL。聚合和台账 SQL 设置 3 秒超时。Redis connect/command timeout 由运行 profile 设置。此模块没有另加 Caffeine L1。

## 可观测与对照

现有 Micrometer registry 中新增：

| 指标 | 说明 |
|---|---|
| `cmms.query.cache.requests{result=hit\|miss}` | 每个通过授权、进入缓存的请求计一次 |
| `cmms.query.cache.errors{operation=...}` | Redis 读/解码、写、加锁、解锁错误；一次请求可能有多个操作错误 |
| `cmms.query.rebuild` | 实际数据库重建耗时 Timer |
| `cmms.query.overload` | 等待超时、容量不足等返回 503 的次数 |

不使用企业、设备或用户 ID 作为指标 label。通过受控 Actuator metrics 或已配置的 Prometheus exporter 查看；未默认开放公网监控端点。p95/p99 用 k6 原始结果或显式配置的 Timer histogram 计算，不能由平均数推断。

`MaintenanceLedgerJpaRepository` 提供同契约 JPA DTO/native SQL 对照；MP 仅扫描 `readmodel.mapper`，不暴露 BaseMapper 写操作。比较时固定 SQL 语义、数据集、分页、权限和连接池，不把全实体加载与少量列投影的差异归因于框架名称。

隔离性能脚本：

```powershell
$env:LAB_URLS = "http://127.0.0.1:28089,http://127.0.0.1:28090"
$env:ASSET_ID = "101"
$env:COMPANY_ID = "11"
$env:START = "2026-01-01T00:00:00Z"
$env:END = "2026-02-01T00:00:00Z"
k6 run perf/asset-overview.js
```

只有一个隔离实例时设置 `LAB_URL=http://127.0.0.1:28089` 并移除 LAB_URLS。令牌通过 LAB_TOKEN 提供。脚本发送实际的 POST JSON，轮询两个隔离端口并检查指标版本/设备/计数关系。无缓存对照在相同实例配置中关闭 `enhancement.query.cache.enabled`，其余条件保持一致。此文件不记录未经运行的性能提升。

## 自动化复现

使用 Java 17，在仓库根目录：

```powershell
mvn -f api/pom.xml "-Dtest=OverviewQueryTest,QueryAccessPolicyTest,AssetOverviewCacheTest,QueryServicesTest,MaintenanceQueryControllerTest" test
mvn -f api/pom.xml "-Dtest=QueryInfrastructureIntegrationTest" "-Dcmms.test.jdbc-url=jdbc:postgresql://127.0.0.1:25432/cmms_enhancement_test" "-Dcmms.test.jdbc-user=cmms_test" "-Dcmms.test.jdbc-password=cmms_test_local" "-Dcmms.test.redis-host=127.0.0.1" "-Dcmms.test.redis-port=26379" test
```

基础设施测试只在显式提供 `cmms.test.jdbc-url` 且数据库名包含 `_test` 时启用；它不读取原部署 datasource。每次创建独立 schema，全部数据为合成维修记录，结束后删除自己的 schema/key。Redis 和 PG 使用单独的本地测试服务。若基础设施未提供，显示 disabled，不算通过。

覆盖：新鲜权限撤回、权限先于缓存、参数规范化、Redis hit/miss/错误回源、Lua 安全释放、两独立缓存对象共享、JPA/MP 台账等价、null 设备、来源筛选、分页/枚举、事务回滚、两个真实并发写者、设备往返迁移/重建、TRUNCATE、旧读在新写后回填，以及真实 Redis 连接失败。测试通过数量以 surefire 报告为准。
