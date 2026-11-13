# Atlas CMMS：Java 后端工程审计与校招二次开发路线

> **Git 恢复补记（2026-11-13）**：原审计目录保持原样；本报告与 7 个证据文件已迁入保留官方历史的个人 Fork。逐文件比对确认基线为 `06abfe35d09372ff80f889fcde73eb3cc71dc205`：官方 2,503 个文件中，2,502 个逐字节一致，唯一差异是根 `.gitignore` 的本地运行排除规则，无缺失文件。因此下文“没有 .git、无法确认本地 SHA”是原审计时的历史状态，不再表示新开发目录的状态。原 `source-fingerprints.json`、测试摘要和探针结果保留当时事实；迁移后的验证另见 [Git 恢复验证](audit-evidence/git-recovery-verification.md)。源码链接已改为仓库相对路径；不把官方功能或历史测试结果冒充个人新增成果。

审计日期：2026-11-13（Asia/Shanghai）。对象：`E:\intern projects\cmms` 本地源码快照。目标：首次系统学习真实 Java 后端工程的硕士，6–10 周完成可解释、可测试的个人增强。

## 0. 结论与证据边界

**最推荐的三个方向：仪表异常触发的状态与幂等、事务 Outbox 驱动的可靠通知、统计口径与缓存一致性。** 它们形成“采集读数 → 生成维修工单 → 可靠通知 → 正确统计”的闭环，分别体现领域建模与并发、事务与异步可靠性、数据准确性与查询优化。保留 Java 17、Spring Boot 3、JPA、PostgreSQL、Quartz、Caffeine；初期不需要 Redis、MQ、微服务或 AI。

这不是“全面安全认证”。本轮详细追踪下述六条链路、四个指定 Issue，并检索了相关工程基础设施。没有逐行检查全部 773 个生产 Java 文件，没有进行真实并发压测、故障注入数据库测试或外部投递。**不得将“源码未找到”写成“整个产品绝不存在”，不得把 Mockito 测试通过写成事务隔离已验证。**

证据分级：

- **R（本地执行复现）**：实际调用当前编译类或检查运行状态，说明测试边界。
- **S（源码确认）**：方法、字段、调用顺序或配置确实如此；不自动等同线上故障。
- **H（待验证风险）**：根据调用链推导，需要隔离数据库、双用户或并发实验确认。
- **N（缺失/规划）**：在本次检查范围未找到闭环实现；属于候选增强。

文中 `[Sxx]` 对应末尾源码索引，含实际文件、类/方法与行号。行号基于当前本地文件；后续修改后应更新。运行证据在 `local-runtime/logs/`，不是 README 推断。此次仅新增审计文档/证据文件和构建测试产物，未改业务源码、数据库配置、许可证检查或现有示例数据。

## 1. 版本、架构与运行基线

### 1.1 本地版本身份

- `git status --short`、`git rev-parse HEAD` 均返回 `not a git repository`：本地没有 `.git`，**当前分支与 Commit SHA 无法确认**。这是下载源码目录，不能直接进行可信的历史差异或 blame 分析。
- GitHub API 本次读取：仓库创建于 **2025-02-17 20:53:50 UTC**；默认分支 `main`；`pushed_at=2026-11-09 22:15:29 UTC`；默认分支最新返回提交为 `06abfe35d09372ff80f889fcde73eb3cc71dc205`，提交时间 **2026-11-05 22:08:52 UTC**，信息为增加连接池至 50。**这是远端信息，不是本地 SHA**。[仓库](https://github.com/Grashjs/cmms)；[远端提交](https://github.com/Grashjs/cmms/commit/06abfe35d09372ff80f889fcde73eb3cc71dc205)。
- 仓库创建日期不代表代码起源：本地迁移历史包含 2023 年文件。没有用文件时间推断某功能的真实开发日期。
- 附件 `audit-evidence/source-fingerprints.json` 保存本次源码指纹。第一周应建立有明确 upstream commit 的版本基线，再迁入本地部署差异；不要用远端 SHA 给现有目录冒充版本。

### 1.2 技术版本：声明、产物、服务分开

| 组件 | 已核实版本 | 证据与解释 |
|---|---|---|
| Java | 项目目标 17；本机运行 17.0.8 | `pom.xml`、启动日志；不是 Java 21 |
| Spring Boot | 3.5.16 | POM parent 和运行日志 [S01] |
| Spring Data JPA / Commons | 3.5.13 / 3.5.13 | 已构建 `app.jar` 的 `BOOT-INF/lib` |
| Hibernate ORM / Envers | 6.6.53.Final / 6.4.4.Final | jar 实际依赖；Envers 在 POM 独立锁旧版，兼容性需要专项验证，不能仅凭版本差异断言当前运行失败 |
| PostgreSQL JDBC / 服务 | 42.7.11 / 16.16 | jar 与只读 `SELECT version()`；Compose 是 `postgres:16-alpine`，不是固定 16.16 |
| Liquibase | 5.0.3 | POM 与 jar；本机 `databasechangelog` 有 815 项 |
| Quartz | 2.5.2 | jar；JDBC job store [S02] |
| MinIO Java SDK | 8.6.0 | POM / jar；不要与存储服务版本混淆 |
| 本地 S3 存储 | PGSTY SILO `RELEASE.2026-09-16T00-00-00Z` | 与 Compose 声明一致，本地原生程序提供兼容接口 [S44] |
| Caffeine / Bucket4j | 3.2.4 / 8.10.0 | jar / POM，并已追到具体调用 |
| Micrometer | 1.15.12 | jar 中已有 core；不等于 Prometheus 看板已配置 |
| 测试 | JUnit Jupiter 5.12.2、Mockito 5.17.0、Testcontainers 1.19.3、JaCoCo 0.8.12 | BOM / POM、测试源码 [S01][S43] |
| 前端 | React 17、CRA 5；本地构建 Node 22.23.5 | 已有部署基线，本轮不重新构建前端 |

### 1.3 目录与运行方式

`api/` 是 Spring Boot 单体，`controller → service → repository` 为主要分层；JPA Entity、MapStruct DTO 映射、业务权限检查与部分 Controller 内业务逻辑并存。`frontend/` 是 Web 客户端；`mobile/` 为移动端；`home/` 为站点；`dev-docs/`、`scripts/` 是辅助资料。运行环境放在忽略提交的 `local-runtime/`，不应纳入源码 PR。

数据库为共享表加 `company_id` 的企业隔离，而非每租户独立数据库。后台有 Quartz、异步线程池、邮件、WebSocket/移动推送、S3 文件服务。缓存为进程内 Caffeine，限流桶为进程内 Map。**这是有后台调度与外部副作用的单体产品，不是已经完成分布式一致性的微服务系统。** [S02][S22–S25][S34–S37]

Compose 包括 PostgreSQL、API、frontend、SILO、Nginx；API 镜像默认未锁 digest/tag，部署前应固定来源。`.env.example` 是配置模板，真实凭据不应写进报告。数据库通过 `db/master.xml` 引入迁移，Hibernate `ddl-auto=validate`；Quartz 表由迁移管理，`initialize-schema=never`。Dockerfile 构建用 `mvn clean package -DskipTests`，镜像构建本身不能替代测试。[S02][S44–S46]

本机已部署入口 `http://127.0.0.1:3089`，API `18089`，PostgreSQL `15432`，S3 `19089`；均为本机回环。启停与检查使用 `local-runtime/start-cmms.ps1`、`stop-cmms.ps1`、`check-cmms.ps1`。邮件通知未开启，本轮未发邮件、Webhook 或其他真实外部业务请求。

### 1.4 本轮执行结果

| 检查 | 结果 | 限制 |
|---|---|---|
| 本地 Git 基线 | 无 `.git` | 无法确认本地 SHA、分支、未提交改动 |
| 主代码/测试编译 | 成功；773 个生产源文件、93 个测试源文件 | 是当前 Maven 测试生命周期的编译，不是全量功能证明 |
| 首次离线测试 | 失败，缺 `surefire-junit-platform:3.5.6` | 环境依赖缺失，不是业务断言失败 |
| 补齐依赖后的指定单元测试 | **474 项，失败 0、错误 0、跳过 0** | 仅下列 10 个顶层测试类及其嵌套测试；未执行全部测试 |
| 4 项外置 Java/Mockito 探针 | 全部完成，复现具体旧行为 | 无 Spring 事务代理、无数据库、无并发验证 |
| Web / API / S3 健康接口 | HTTP 200 | `/health-check` 本身不证明全部依赖；另测数据库 SELECT 1 |
| 数据库 | PostgreSQL 16.16；815 项迁移；当前 8 张工单 | `BEGIN READ ONLY`；没有插入压测数据 |
| Docker / 集成测试 | `docker` 命令不可用，未执行 Testcontainers 集成测试 | 不把原生 PostgreSQL 偷换成测试容器，不指向现有 atlas 库跑测试 |
| 完整覆盖率/性能 | 未取得本次全量覆盖率；未压测 | 474 项通过不等于 100% 覆盖；没有性能提升百分比 |

测试类：`WorkOrderServiceTest`、`RequestServiceTest`、`PartServiceTest`、`ScheduleServiceTest`、`RefreshTokenServiceTest`、`NotificationServiceTest`、`CacheKeyUtilsTest`、`CacheConfigTest`、`TenantAspectTest`、`WorkOrderCreationJobTest`。

本机复现命令（PowerShell，Maven/JDK 路径取自当前环境）：

```powershell
Set-Location 'E:\intern projects\cmms\api'
$env:JAVA_HOME="D:\C's helper\JDK 17"
$env:MAVEN_OPTS='-Xmx384m'
& "D:\C's helper\apache-maven-3.9.4\bin\mvn.cmd" -B `
  -s '..\local-runtime\config\maven-settings.xml' test `
  '-Dtest=WorkOrderServiceTest,RequestServiceTest,PartServiceTest,ScheduleServiceTest,RefreshTokenServiceTest,NotificationServiceTest,CacheKeyUtilsTest,CacheConfigTest,TenantAspectTest,WorkOrderCreationJobTest' `
  '-Djunit.jupiter.execution.parallel.enabled=false' `
  '-DargLine=-Xmx512m --add-opens java.base/java.lang=ALL-UNNAMED'
```

本轮显式覆盖 `argLine` 以限制资源，**不引用 JaCoCo 覆盖率**。POM 中有两个 `argLine` 定义，需要后续整理为单一配置，兼顾时区、JaCoCo 与 JVM 参数。[S01]

测试结构包括 Service Mockito、Controller WebMvcTest、数据库集成、配置/security/utils/template/job。集成基类明确启动 `postgres:16-alpine`，库名 `cmms_test`，并覆盖 datasource，带共享资源锁。现有范围有 WorkOrder/Request/Asset/Meter/PM 等，但本次未发现 ReadingService、WebhookDispatchService、PartQuantityController 的直接同名测试，不能以大量其他单测替代这些关键链路验证。[S43]

CI `main-ci.yml` 已有 `mvn test`、自托管 Compose smoke、镜像推送和 Koyeb 部署，不应重新宣称“从零加入 CI”。但触发器主要是 main push/tag，测试可被 `SKIP_TESTS` 跳过；镜像发布/部署 job 依赖 test，却未以 selfhost smoke 成功为共同门禁。应增强 PR 检查与发布依赖。本轮未触发远端 workflow、未核实所有历史 CI 是否通过。[S42]

## 2. 六条核心业务链路

### 2.1 工单创建、分派、执行、完成

**调用链**：`WorkOrderController.create` → `WorkOrderService.createByUser` → `create` → `WorkOrderRepository.saveAndFlush` → 通知 / Workflow / Webhook。分派走 `patch → update → patchNotify`；状态变化走 `changeStatus`。[S06–S09]

DTO `dto/workOrder/WorkOrderPostDTO` 直接继承 `WorkOrder`，不是严格收缩的创建命令；公共属性来自 `WorkOrderBase`。实体关联 `company`、asset、location、team、primaryUser、assignedTo、parentRequest、parentPreventiveMaintenance；涉及 `work_order`、关联表、`labor`、`task`、通知及审计表。[S10][S11][S47]

创建入口有 WORK_ORDERS 创建权限，自动分派开关可填入当前用户；`create` 清理 id、完成信息等后保存，分配企业自定义序号，执行创建 Workflow 并通知。`patch` 先判断可编辑权限，再保存并通知新增分派对象。`changeStatus` 会设置首次响应时间、完成时间、停止工时、尝试结束设备停机、为按完成时间计算的 PM 排下一次任务，并通知请求人。[S06–S08][S48]

**事务**：`createByUser/create/patch/changeStatus` 都有事务边界；`saveAndFlush` 不等于 commit，后面的异常仍可回滚。`notify` 调用的异步服务不参与原线程事务。状态方法当前非严格迁移表，对重复 COMPLETE、重开、并发修改需明确定义；WorkOrder 没有 `@Version`，重复完成是否改变 completedOn 及重复排下一任务需集成验证。[S08][S11][S22–S24]

**权限/隔离**：Controller 方法角色 + Service/实体权限 + CompanyAudit PostLoad/PrePersist + 请求体 TenantAspect 多层共同作用。不能只看到 `findById` 就断言跨企业越权，也不能把实体回调当作适用于异步任务、native SQL、所有缓存命中的完备隔离。[S34–S36]

**异常**：权限不足、签名限制/格式、数量限制抛业务异常；若 `Optional.get()` 未先处理空值，则某些路径可能不是约定的 404。外部通知失败与工单保存不能构成原子提交。序号是读改写而非原子 SQL，另见风险 F10。[S06][S08][S39]

### 2.2 仪表读数异常生成工单

`ReadingController.create/patch` → `ReadingService.create/patch` → `processMeterTriggers` → `WorkOrderMeterTriggerService.findByMeter` / Repository → `WorkOrderService.create`。表：`meter`、`reading`、`work_order_meter_trigger`、`work_order`；读取 DTO 为 Reading / ReadingPatchDTO，阈值实体继承 WorkOrderBase 以充当工单模板。[S03–S05][S49]

`create` 在事务中检查订阅 METER 功能、仪表可见权限和按天更新频率，**先触发工单再保存读数**；`patch` 保存编辑值后无条件重评触发器。阈值比较为严格 `<` / `>`；每次满足条件即通知、创建工单、发 Webhook。没有读取 recurrent/waitBefore；阈值是 int，读数为 double。[S03][S04]

Reading 和工单通过外层事务可同回滚，但异步通知可能已发出。未找到触发状态、触发周期或工单来源 FK；数据库也没有相应唯一键。两个并发请求可以同时通过最后读数检查，再重复触发。已复现的是核心方法两次创建调用，**没有声称双事务重复入库已经实测**。编辑历史读数会重评当前触发器，不是只评最新样本。[S03–S05]

### 2.3 预防性维护生成与执行

`PreventiveMaintenanceController.create` → `PreventiveMaintenanceService.create` → 保存 PM 与 Schedule → `ScheduleService.scheduleWorkOrder` → Quartz JobDetail/Trigger → `WorkOrderCreationJob.executeInternal` → `createWorkOrderFromPreventiveMaintenance` → 复制模板、关联 parentPreventiveMaintenance、复制检查任务、创建工单。[S13–S16][S50]

表：`preventive_maintenance`、`schedule`、`work_order`、`task`、Quartz `qrtz_*`。支持按计划日期和按完成日期、日/周/月/年频率、结束日期、disabled、企业时区、提前通知；连续五条未响应工单会影响调度启用。不能说“没有定时任务、没有停用/时区/misfire处理”。[S15]

Service 已有事务，任务创建方法也有事务标注；但 Quartz 通过继承的 final `QuartzJobBean.execute` 调用内部方法，**注解出现在 executeInternal 不自动证明 Quartz 入口事务代理生效**。下游 PM Service 的公开事务方法仍有独立保护，具体 Quartz/业务表事务联动必须做集成断言。运行日志也出现 final 方法代理警告。[S14][S16]

当前 WorkOrderCreationJob 有 schedule 不存在、disabled、周间隔校验，异常记录并抛出；未找到该 Job 的 `@DisallowConcurrentExecution`、执行批次唯一键、业务补偿账本。稳定 job key 可抑制重复注册，不保证“一次计划时间只生成一次业务工单”。未配置已验证的 Quartz 集群，单机日志为 NON_CLUSTERED。[S02][S15][S16]

### 2.4 备件领用、库存和采购

`PartQuantityController.patchWorkOrder/patch/delete` → `PartService.consumePart` + `PartQuantityService.create/update/delete`；补库走 `PartController.restock → PartService.restockPart`；采购走 `PurchaseOrderController.respond → partService.save → purchaseOrderService.save`。[S17–S21]

表：`part`（数量、成本、version）、`part_quantity`（工单/采购单与备件的数量关系）、`part_transaction`（库存变化）、`purchase_order`。DTO `PartQuantityPatchDTO` 接收目标数量，consume 计算新旧差额。原项目**已有 Part @Version，已有乐观冲突 HTTP 409**，不应重新描述为“从零引入库存乐观锁”。[S19][S51][S52]

缺口：Controller 没有统一业务事务，consume 和 PartQuantity 更新是不同调用；中途失败可能已经扣库却未更新领用明细。`patch` 在检查工单可编辑权限之前调用 consume；`patchWorkOrder` 先更新首次响应时间再检查可编辑权限。批量关联删除某个备件与单条 DELETE 的返库行为不一致；同数量重复提交算出 delta=0，却进入删除最近流水分支。采购批准中遍历补货后再保存 APPROVED，没有同一总事务及审批版本控制；部分成功、并发重复批准为 H，须单独验证。[S17][S18][S20]

当前采购批准即增加库存，不是已有“采购审批—到货—验收”完整分离模型。不要在简历上声称已实现采购收货幂等。非库存备件、允许数量为 0、小数单位与库存流水的语义，需要先写规则，再加锁。金额 double/long 混用也不宜直接用于精确财务结算。[S18][S20][S51]

### 2.5 工单通知、Webhook 与统计

工单事件直接调用 `NotificationService.createMultiple`、`EmailService2.sendMessageUsingThymeleafTemplate`、`WebhookDispatchService.dispatchWebhook`。Notification 表持久化后发 WebSocket/移动推送；Webhook 按企业订阅配置筛选端点，生成 HMAC、时间戳、随机 ID，并校验 URL。SMTP 实现有启用开关。**这些功能已有，不是待从零新增。** [S22][S23][S53]

异步线程池为 3 线程、队列 11，传播 SecurityContext，未传播事务。异常处理接 Sentry。Webhook 用默认 RestTemplate，当前类没设置明确连接/读取超时，失败日志后没有投递表和重试任务；随机每次 ID 不构成稳定事件身份。没有已验证的可靠持久队列；事件可能在业务回滚前外发，进程退出可能丢失排队任务。[S23][S24]

统计走 `WOAnalyticsController` → WorkOrderService → WorkOrderRepository，部分在 Java 遍历实体，部分已有 native 聚合 SQL。成本计算部分按每张工单查 labor/part_quantity/additional_cost，有 N+1 查询形态；Repository 已有预聚合子查询，不能把全部统计说成低效逐条查询。缓存放在 Controller 的返回值上，失效和授权边界另见 #318。[S27][S28][S54]

### 2.6 报修审批作为领域扩展入口

`RequestController.approve` → `RequestService.approve` → `createWorkOrderFromRequest`，把 Request 的 workOrder 与 WorkOrder 的 parentRequest 连接起来；事务中运行 REQUEST_APPROVED Workflow、改设备状态、通知请求人。[S29][S30][S55]

当前检测已有 workOrder，但没有同样明确的 cancelled 状态守卫、请求版本锁与既有工单关联命令。多个并发 approve 可能都看到空关联。缺点不是“没有审批”，而是审批状态、幂等和关系模型还不适合多个请求合并到一个工单。[S29][S30]

## 3. 已有能力清单

“已完善”仅用于明确检查过的狭窄职责，不是整个平台保证。本轮对跨模块能力不轻易给满档。

| 能力 | 状态 | 已有实现与边界 |
|---|---|---|
| 认证、RBAC、API key / JWT | 有初步实现但需增强 | Spring Security、实体权限、JWT/refresh；会话粒度与缓存撤权仍需增强 [S31–S36] |
| 企业隔离 | 有初步实现但需增强 | company_id、搜索条件、PostLoad、TenantAspect；非 SQL RLS，异步/native/聚合须回归 [S34–S36] |
| 事务 | 有初步实现但需增强 | 多个核心 Service 已事务化；Controller 跨调用库存/采购不完整 [S06][S17–S21] |
| 乐观锁 | 有初步实现但需增强 | Part @Version + 409；不覆盖工单、报修、refresh、序号 [S19][S52] |
| 悲观锁/通用命令幂等 | 尚未实现（检索范围） | 未找到 Repository @Lock 与统一请求幂等账本；主键/唯一 token hash 不等于业务幂等 |
| Caffeine | 有初步实现但需增强 | users 5min、signedUrls、默认20min；统计无写后失效闭环 [S25–S28][S38] |
| Redis | 尚未实现（后端检索范围） | 没有使用证据；不能据此要求安装 |
| 限流 | 有初步实现但需增强 | Bucket4j、登录/上传/IP/用户桶；本地 Map 无统一 TTL 上限，多实例不共享 [S37] |
| 异步、线程池 | 有初步实现但需增强 | @Async、有界池、SecurityContext、异常捕获；无持久化恢复 [S22–S24] |
| 邮件、Webhook | 有初步实现但需增强 | 模板、HMAC、端点筛选、URL校验；无可靠投递账本、退避和死信 [S23][S53] |
| Quartz | 有初步实现但需增强 | JDBC、稳定 job key、misfire、企业时区；业务去重和集群未验证 [S02][S15][S16] |
| SQL、分页、索引 | 有初步实现但需增强 | Specification/PageRequest、部分 fetch、聚合SQL、现有索引；未建立查询预算及慢SQL基线 [S12][S54] |
| 审计 | 有初步实现但需增强 | Envers、work_order_history；不是每项业务命令都有完整审计事件 [S11][S56] |
| 日志/监控 | 有初步实现但需增强 | SLF4J、Actuator、Micrometer传递依赖、Sentry及Quartz listener；未见完整业务指标看板 [S01][S24][S40] |
| 测试 | 有初步实现但需增强 | 已有93个Java测试相关源文件，选定474项通过；并发、回滚、Reading链路等有缺口 [S43] |
| Docker / CI/CD | 有初步实现但需增强 | Compose、镜像、test与smoke、部署；版本固定与发布门禁可增强 [S42][S44–S46] |
| 事务 Outbox、可靠事件重放 | 尚未实现（检查链路） | DemoDataService 有 AFTER_COMMIT 不等于业务事件通用 Outbox [S57] |
| 完整 SLA 升级 | 尚未实现（检查范围） | 已有 dueDate/首次响应/完成/PM提醒和Workflow条件，未见SLA策略、暂停计时、升级台账 [S08][S15][S41] |
| 生产吞吐、SLO、全量覆盖率 | 无法确认 | 无生产指标、压测报告或本次完整覆盖率数据 |

## 4. 四个指定 Issue：当前适用性与方案

本次 GitHub API 返回四项均为 open，评论数均为 0；open 不是缺陷成立的证据，下面以本地代码复核。

### 4.1 #320：仪表重复触发——核心问题仍在

[Issue #320](https://github.com/Grashjs/cmms/issues/320)。源码 [S03–S05] 与探针 R：阈值10，先15后16，`recurrent=false`、`waitBefore=999`，核心方法仍两次调用 create。编辑路径无新旧值比较、无“最新读数”检查。WorkOrder 没有 parentMeterTrigger。[S11]

**不要把三个问题混为一谈**：同一个请求重试、持续异常的多次采样、异常恢复后再次异常是三个不同身份。并发锁也不能替代产品语义。

方案一：**活动工单去重，作为最小版本**。

- 增加工单来源 trigger_id，定义同触发器最多一张活动工单；事务内锁触发器行，查询活动工单再创建。
- PostgreSQL 增加部分唯一索引兜底，例如 `(company_id, meter_trigger_id)`，条件为来源不空且处于约定活动状态。当前 Status 是 ordinal，用 enum 的持久值时必须确认映射并写迁移测试，不能盲写字符串状态。
- 对读数提交增加来源请求 ID/幂等键唯一约束；频率校验与写入在同一个受锁保护的事务内。相同键不同 payload 返回409。
- 编辑默认只修正记录，不产生新工单；若支持重评，只允许最新读数，记录明确的重评命令。
- 优点：范围小，约束明确；缺点：持续异常期间人工完成工单后下一次读数可能再开单，不能完整表达“恢复再武装”、冷却、持续时长。必须把这个取舍写进接口说明。

方案二：**异常周期状态机，作为推荐增强**。

- 新增 `meter_trigger_state`：company/trigger、状态 NORMAL/PENDING/ACTIVE/COOLDOWN、episodeId、lastSampleTime/lastReadingId、firstAbnormalAt、lastTriggeredAt、activeWorkOrderId、version。
- NORMAL→PENDING：首次异常；达到持续时长/样本要求后 PENDING→ACTIVE，原子创建工单和 episode；正常读数使异常恢复并按规则进入 COOLDOWN/NORMAL；再次异常产生新 episode。冷却结束不一定要定时扫描，可在下次新样本到达时推进，需明确恢复规则。
- 正常恢复与人工关闭工单分开；recurrent=false 到底“一生仅一次”还是“每次恢复后可再触发”须确定，不能从变量名猜。定义 waitBefore 的单位和版本化 API，阈值改为可表达小数的类型并测试边界。
- `(trigger_id, episode_id)` 唯一关联执行记录；按 `(meter_id, source_event_id)` 做读数幂等；锁固定顺序为 meter→trigger/id升序。乐观锁方案可用 version 条件更新抢占状态，但冲突后须重新读和重算，不能重试带外部副作用的整个旧方法。
- 重复/乱序读数先按业务时间及序号判定；历史纠错不倒退状态。阈值相等、NaN、Infinity、负值、计量单位需要输入验证。累计计数器“每500小时”属于后续独立规则，不塞入首版。
- 工单+状态+Outbox 同事务提交。worker 只消费已提交事件；唯一 `(event_id, channel, recipient)` 抑制本地重复。外部发送成功但未标记成功时仍可能重复，要求接收方按稳定 eventId 幂等，不能宣称网络外部效果 exactly-once。

**测试设计**：独立 PostgreSQL Testcontainers，两个真实事务以 barrier 同时处理相同 trigger；不是共享一个测试事务内顺序调用。覆盖重复请求、同键异载荷、20次持续异常、恢复再异常、编辑历史、并发冷却到期、工单保存失败、Outbox插入失败、死锁/乐观冲突重试。验收：每 episode 工单数=1，失败事务状态/读数/工单/事件均回滚；没有确认的 episode 不发通知。数据库并发测试尚未执行。

### 4.2 #318：统计缓存与准确性——核心问题仍在

[Issue #318](https://github.com/Grashjs/cmms/issues/318)。[S25–S28][S54]

已确认：默认20分钟 TTL；时间范围键向下归到20分钟桶；控制器查询用原始起止时间。探针复现两个不同精确区间的相同 key。旧单测甚至验证“同桶得到同键”，它证明实现符合旧设计，不证明查询语义正确。MTTA按整小时截断，周期按整天截断；20小时返回0天已复现。所检查统计控制器无 filterFields 的完整消费，新增筛选必须同时改变查询与缓存键。

陈旧场景：缓存预热后完成/重开工单、更新备件数量/成本、人工工时修改，仍命中旧响应；搜索统计缓存失效注解/调用未找到写入端失效闭环。TTL 不是提交后刷新，也不是严格一致。

隔离判断：key 包含 userId，示例 getCompleteStats 还带可选 companyId；正常数据库全局用户ID下，**不能声称已经出现跨用户直接串缓存**。但 `canSeeAnalytics`、`resolveCompanyId` 在被缓存方法体内，缓存命中不会重新执行内部授权。用户失去统计权限或子企业访问权后，旧 key 是否继续返回结果属于 H，应做撤权后同参数请求测试。授权必须先于缓存，key 用已授权的 resolvedCompanyId/权限范围版本，不能只信客户端 companyId。[S27][S36][S58]

推荐：先写清指标口径（创建期 cohort vs 完成期流量、修复时长 vs 周期、暂停是否排除、时区、空样本）；用秒/毫秒和 double/BigDecimal 汇总后展示舍入；统一半开区间 `[start,end)`。将缓存移至已授权的查询服务，精确规范化键包括企业、可见范围、筛选、时区、指标版本和完整时间；不再用20分钟桶代表不同查询。

单实例可以先用事务提交后事件做相关统计失效；全清简单但影响其他企业，按企业 generation/version key 更可控。注意“先读旧数据、写事务提交并失效、旧读又回填”的竞争，若要严格新鲜度需版本校验/读取策略。多实例可靠失效可消费 Outbox，并保留TTL兜底；不能认为 Redis 天然解决失效和授权。

**性能实话**：已看到 Java N+1 形态和跨全表子聚合形态，但没有证据说它们在你的8张示例工单下是瓶颈。候选索引 `(company_id, created_at)`、`(company_id, completed_on)`、`reading(meter_id,created_at desc)`、明细表 work_order_id 必须先看 EXPLAIN 与已有索引，不能全加。数据库元数据已确认 work_order 目前只有主键和 company_id 索引；这支持实验假设，不直接支持性能提升结论。

测试：精确时间区间碰撞、40分钟MTTA、20小时周期、空集合、跨日时区、筛选变更、两个企业同时间、撤权、回滚不失效、提交后变更、旧读回填。性能使用专用合成数据10k/100k工单、多个企业、真实明细分布，比较冷缓存/热缓存、SQL条数、扫描行、buffer、p50/p95、应用CPU/内存；记录同一环境和原始结果。目标不是预设“提升80%”。

### 4.3 #353：多个请求关联现有工单——缺少业务命令，模型也需调整

[Issue #353](https://github.com/Grashjs/cmms/issues/353)。当前只有 approve/cancel；approve 创建新工单。数据库只读查询确认 request.work_order_id 无唯一约束，但实体仍 `@OneToOne`；`@PreRemove` 无条件清空关联工单的 parentRequest。[S29][S30][S55]

因此不同意“不需要任何模型/迁移考虑”的简单结论：应改成 Request→WorkOrder 的 `@ManyToOne`，检查所有历史关联、序列化、删除回调和统计口径。保留 WorkOrder.parentRequest 为原始来源，关联新请求不覆盖它；删除后加关联的请求只能清理自己的关联，不能误清原始来源。当前库不需要删除已有唯一键，但其他安装可能不同，Liquibase 应用 precondition 明确检查，补索引而不盲删。

新命令 `PATCH /requests/{id}/link-work-order`，精简 DTO `{workOrderId, expectedVersion/requestKey}`。事务内先查公司范围和权限，要求 pending（未取消且未关联）；锁 Request 或 CAS version，目标工单同企业且调用者可见。重复关联同目标可返回当前结果，不同目标409；与 approve/cancel 竞争只能一个成功。是否允许关联已完成工单做成明确产品规则（首版可允许并提示，不重开工单）。

记录操作者、原状态、目标工单、原因/关联时间；Outbox 通知请求人及状态Webhook（保持许可证门禁）。不能直接重跑 REQUEST_APPROVED 工作流，否则可能再产生创建工单等副作用；新增命令语义/事件类型或显式 linked 标记。兼容旧 API 与原始 parentRequest。测试覆盖并发关联、关联/审批竞争、跨企业、取消请求、删除关联请求、旧数据加载、原工单时长不被新报修时间改变。

### 4.4 #361：多设备退出——当前仍是全部退出

[Issue #361](https://github.com/Grashjs/cmms/issues/361)。`AuthController.logout` 无请求体，调用 `UserService.invalidateSessions`，更新 sessionInvalidatedAt、清本地用户缓存、撤销全部 refresh。JwtTokenProvider 用 iat 与用户失效时间比较。已有哈希保存 refresh token、轮换、过期检查和重用检测，不能重新声称“新增 Refresh Token 系统”。[S31–S33][S38]

方案一（兼容小步）：logout 接可选 refreshToken，校验归属，只撤当前刷新令牌；无请求体保持老客户端全部退出行为，新增 logout-all；Web/移动端逐步发送 token。必须说明旧 access token 在到期前仍可用，不把客户端删除 token 当服务端立即撤销。

方案二（严格会话）：新增 auth_session，JWT 带 sid，refresh 关联 session/family；当前设备退出撤销 session，全部退出使用 user sessionVersion/invalidatedAt；每次鉴权查状态或短TTL缓存配失效。保存哈希，不记录原始token。成本是额外查询/缓存一致性与客户端迁移。

独立发现：`rotate` 无行锁/version；两个并发请求可能同时轮换同一 token。重用分支调用 revokeAll 后抛 RuntimeException，整个 rotate 事务可能回滚撤销；现有 Mockito verify 不能证明提交。建议用独立事务事务模板/明确返回失败后由外层抛异常，让安全撤销真实提交，同时考虑访问令牌是否撤销、token family 误报和客户端多标签页正常刷新竞争。**此事务后果为 H，未用真实 DB 复现。** [S32][S59]

## 5. 独立发现与缺陷分级

除四个 Issue 外至少以下十项值得调查；优先级针对下一阶段验证，不代表已在线利用。

| ID | 类型/优先级 | 发现、证据与边界 |
|---|---|---|
| F01 | R+H / P0 | PartQuantity.patch 先调用 consume 再判断可编辑权限。探针确认调用顺序及最终拒绝；库存是否已提交需真实代理DB测试。同企业低权限角色即可构造验证，不必声称跨租户漏洞。[S17] |
| F02 | S+H / P0 | 扣库/流水与领用明细分属多个 Service 调用，Controller无总事务；明细保存失败可能只扣库存。采购补库和批准也分离。[S17–S21] |
| F03 | S / P1 | 目标数量不变时 delta=0，consumePart 删除最新 PartTransaction；重复提交改变审计流水。还需接口/数据库测试确认完整结果。[S18] |
| F04 | S+H / P1 | patchWorkOrder 移除关联只删明细，而单项 DELETE 会返库；应确定替换集合命令的库存语义，防止相同UI操作走不同结果。[S17] |
| F05 | S+H / P0验证 | refresh 重用检测撤销后抛 RuntimeException，可能回滚撤销；并发轮换无串行化。会话命令须验证真实提交。[S32][S59] |
| F06 | S+H / P1 | 工作线程数3、队列11；外部Webhook无显式超时、无投递重试账本；慢端点可耗尽队列，异步拒绝也可能影响调用线程。[S23][S24] |
| F07 | S+H / P1 | WorkOrder.update 复用可变 webhookPayload，第一次异步提交后继续增加分类变化字段；异步服务本身也put，可能出现事件快照混杂。改不可变DTO并做受控线程测试。[S07][S23] |
| F08 | S+H / P1 | Quartz无业务执行唯一键，缺明确并发禁止；稳定jobKey不保证重复触发幂等。executeInternal事务代理不能由注解位置推断。[S15][S16] |
| F09 | S+H / P2 | Bucket4j 用多个 ConcurrentHashMap.computeIfAbsent，没有全局有界淘汰，随机标识会持续扩容；多实例额度按实例叠加。先压测单机内存再谈Redis。[S37] |
| F10 | S+H / P1 | CustomSequence 读后自增再save，无 version/锁；同公司并发可重复业务编号。需要原子UPDATE RETURNING/行锁及(company,customId)唯一约束，不能用synchronized应付多实例。[S39][S60] |
| F11 | S+H / P1 | 缓存命中绕过方法内权限/企业可见性检查；key已有userId，不是已确认跨用户泄露。撤权与父子企业关系变更必须测试。[S27][S36][S38] |
| F12 | S+H / P2 | ORM与Envers版本不同、Surefire argLine重复；当前可编译并运行，不代表审计历史与测试参数组合始终正确。先依赖树/审计回归，不整体升级Boot。[S01] |

不将 F01/F11 直接写成“已修复越权”：本轮没有修复，也未用真实角色会话修改业务数据。P0 含义是**扩展前先验证或处理**。如果复现影响权限/库存的持久化缺陷，应优先修复，主线功能顺延，而非为了进度跳过。

## 6. 技术选型：用现有体系解决问题

| 候选技术 | 本项目判断 | 引入条件/不引入理由 |
|---|---|---|
| Redis | 初期不引入 | Caffeine已有；只有多实例共享限流、跨节点会话/缓存确需时引入。必须处理失效和授权，不能只换CacheManager |
| RabbitMQ / RocketMQ | 初期不引入；进阶择一 | 原业务量未测；数据库Outbox+轮询可先完成可靠性。多消费者吞吐或系统边界确有需求再加MQ，仍保留Outbox与消费幂等 |
| 事务 Outbox | 推荐 | 数据库提交和通知排队不原子是明确设计缺口。JPA+PostgreSQL即可实现 |
| 锁、CAS、唯一约束 | 推荐 | trigger episode、库存命令、请求关联、refresh轮换都有实际竞争；每个场景单独选粒度 |
| PostgreSQL优化 | 推荐先测量 | 保留数据库，结合EXPLAIN、聚合projection、索引、分页大小限制；不迁MySQL |
| Spring Security | 增强已有 | 企业范围查询、方法授权、变更前校验及测试；不重写登录平台 |
| Quartz | 增强已有 | 运行实例幂等、时区/misfire/补偿；不把新定时器作为成果 |
| Actuator/Micrometer/Prometheus | 前两者已有基础，补业务指标；Prometheus可后加 | Outbox积压、最老事件年龄、失败数、触发去重数、调度延迟有业务含义；避免用户ID作高基数label |
| JUnit/Mockito/Testcontainers | 必须补齐关键链路 | Mockito测逻辑，真实Postgres测事务锁与迁移，不拿H2证明Postgres部分索引 |
| k6/JMeter | 后期择一 | 有正确性和固定数据基线后再用；不能对当前学习库施压 |
| Docker/CI | 增强已有 | PR测试、服务健康门禁、固定版本、独立test profile；不提交本机凭据 |
| Elasticsearch | 当前不推荐 | 工单标题/编号/描述搜索先评估Postgres trigram/全文索引；只有跨实体复杂检索和已测规模才考虑 |

### 三个指定新增功能的适配结论

1. **可靠工单事件与通知中心：增强，不是从零新增通知。** 复用 Notification、邮件模板、Webhook配置与权限，仅新增事件持久化、投递状态、重试/重放和可观测性。先站内消息，邮件用本地假适配器；Webhook商用门禁保持。初期不必MQ。
2. **设备异常与PM规则：增强已有仪表规则。** 先阈值、小数、恢复和重复抑制，再持续异常/冷却；PM按日期生成已存在，不要整体重写成通用规则DSL或Drools。首版不混合累计计数器与自然时间语义。
3. **工单SLA与超时升级：部分复用、主要新增。** dueDate、firstTimeToReact、completedOn、Workflow条件、PM提前提醒已有，但不等于SLA策略。策略版本、暂停/恢复、响应/修复双时限、升级级别和重复升级账本尚未找到。作为第三阶段扩展，先不与前三项同时铺开。

## 7. 可选任务评分与详细任务卡

评分顺序为 **业务价值/Java深度/校招展示/当前可行性/可验证性/维护风险**，各1–5，风险越高越差。分值是基于代码复杂度和学习背景的规划判断，不是测量结果。工作量含阅读、实现、测试、说明，按每天4–6小时有效投入估计；任务重叠不可简单相加，初学者建议留30%缓冲。

| 任务 | 改造内容 | 六项评分 | 粗估工作量 | 优先级 |
|---|---|---|---|---|
| T01 | 仪表触发幂等与异常周期 | 5/5/5/3/5/4 | 8–12人日 | P1主线 |
| T02 | 可靠工单事件Outbox | 5/5/5/3/5/4 | 10–15人日 | P1主线 |
| T03 | 统计精度、授权和缓存正确性 | 5/4/5/4/5/3 | 6–9人日；性能扩展另3–5 | P1主线 |
| T04 | 多请求关联现有工单 | 4/4/4/4/5/3 | 4–6人日 | P1候补/热身 |
| T05 | 分设备退出与refresh并发 | 4/5/5/3/5/4 | 6–9人日 | P0验证/P1实现 |
| T06 | 库存权限顺序、事务与幂等 | 5/5/5/3/5/5 | 8–12人日 | P0验证修复 |
| T07 | Quartz执行幂等与恢复 | 4/5/5/3/5/4 | 6–9人日 | P1候补 |
| T08 | 企业隔离与命令权限回归 | 5/4/5/4/5/3 | 4–6人日 | P0验证、持续门禁 |
| T09 | SLA提醒与超时升级 | 4/4/4/3/5/4 | 8–12人日 | P2 |
| T10 | 有界限流桶与可选共享限流 | 3/3/3/4/4/2 | 本地2–3人日；Redis另3–5 | P2 |
| T11 | 可靠性指标与故障观测 | 4/3/4/4/5/2 | 3–4人日 | P1配套 |
| T12 | 可复现测试、迁移与PR门禁 | 5/3/4/4/5/2 | 3–5人日 | P0基线 |

以下每项“简历”都以独立实现并完成证据为前提；只读源码不能算个人开发贡献。

### T01 仪表规则可靠触发

- **问题/价值/证据**：持续异常重复开单、编辑重触发，业务直接受影响；[S03–S05]、#320、已执行探针。
- **改动范围**：ReadingService、Trigger实体/DTO/Repository、WorkOrder来源、Liquibase，新增TriggerState/TriggerExecution；可选前端阈值与冷却字段。首版无新框架。
- **前置/风险**：事务代理、READ COMMITTED、行锁/乐观冲突、唯一约束、状态机；乱序读数、历史编辑、状态边界、迁移旧工单无来源是主要坑。
- **最小版本**：同触发器单活动工单+来源关联+重复命令去重；之后才加episode恢复模型。
- **验收**：2–20并发/100次重复，同episode仅1工单；恢复后新episode可创建；失败不留半条状态；0跨企业引用。真实Postgres事务测试+Mockito边界测试。
- **简历**：能展示状态机、唯一约束兜底、并发重试和来源可追踪。
- **五个面试问题→必须掌握**：为什么查后插仍重复→隔离级别与竞争窗口；为什么不synchronized→多实例；为什么锁外还要唯一键→最后防线；恢复与完成如何区分→领域生命周期；通知如何不跟着回滚误发→Outbox事务边界。

### T02 可靠工单事件与通知

- **问题/价值/证据**：已有异步没有持久化恢复；[S06][S22–S24][S53]。降低丢失和重复副作用的风险，比“接入MQ”更有价值。
- **改动范围**：新 `domain_event_outbox`、`event_delivery`，业务Service写事件、投递worker、现有通知适配器；状态PENDING/PROCESSING/SENT/DEAD，attempts/nextAttemptAt/leaseUntil/eventId/companyId。必要时站内Notification增加eventId唯一范围。前端首版不必改，重放接口只给管理员。
- **依赖/前置/风险**：现有JPA/Postgres/定时机制；事务、锁、有限状态重试、至少一次语义。不要跨网络持有数据库行锁；短事务claim，提交后发送，短事务ack；租约恢复进程中断。事件只存最小必要字段，避免复制密码/token/完整用户实体。
- **最小版本**：仅工单创建/分派/状态事件→站内通知；假邮件端点模拟500、超时。稳定eventId、按收件人幂等、指数退避加抖动、最大次数转DEAD、授权人工重放。
- **验收**：业务回滚0事件；业务成功后杀worker，重启可继续；重复消费10次站内通知1条；失败上限转DEAD；外部不承诺绝对不重发；跨企业投递0。统计积压/最老事件/延迟，而不是只记录日志。
- **简历**：能展示事务Outbox、恢复、幂等、故障注入。说明个人改造的是可靠性层，模板和推送SDK来自上游。
- **五问→原理**：AFTER_COMMIT为何还会丢→提交后进程崩溃窗口；为何无需MQ→当前吞吐/基础设施成本；发送成功ack失败→至少一次与稳定ID；如何多worker抢占→SKIP LOCKED或CAS/租约；如何避免乱序→聚合版本、同工单顺序及重试策略。

### T03 统计正确性与缓存

- **问题/价值/证据**：时间精度、键碰撞、陈旧缓存、授权位置；[S25–S28][S54]。正确结果优先于性能。
- **改动范围**：CacheKeyUtils、Analytics Controller/查询Service、WOStats DTO、聚合Repository、指标测试；可加缓存epoch表/Outbox失效消费者；前端处理小数单位、更新时间与必要的刷新提示。
- **依赖/前置/风险**：不加Redis；缓存代理、事务提交、SQL聚合、日期区间和时区。权限应在缓存入口之前；不要把“创建期”和“完成期”混成同一个指标。
- **最小版本**：精确键+分钟/秒精度+一个统计接口的提交后失效。若暂做全清，明确单实例可用与性能代价。
- **验收**：固定fixtures与手算一致、撤权后拒绝、不同企业/筛选/时区不串结果、回滚无失效错误；对并发旧读回填写测试。SQL优化只在10k/100k数据计划比较后实施，报告查询条数和p95原值，不预填提升比例。
- **简历**：先写“修复统计精度和缓存一致性”，有性能证据后再加优化结果。
- **五问→原理**：TTL为何不等于一致→时间窗口；精确键如何规范化→时区/筛选排序；清缓存后仍旧→旧读回填；Redis为何不自动正确→分布式失效；如何证明SQL优化→EXPLAIN、相同数据分布、冷/热缓存对照。

### T04 重复报修关联

- **问题/价值/证据**：避免approve额外造单、保留报修者语义；[S29][S30][S55]。
- **改动范围**：LinkWorkOrderDTO、RequestController/Service/Repository、Request关系及删除回调、审计/Outbox；数据库索引与可选version；前端新增“关联现有工单”选择框。
- **依赖/前置/风险**：无新框架；JPA关系、CAS、RBAC、DTO兼容。不能随意修改原始parentRequest或重跑创建型Workflow。
- **最小版本**：同企业pending请求关联可见工单，相同目标重试幂等，冲突409。
- **验收**：双请求可同指向工单；一个请求并发关联/审批仅一项成功；跨企业拒绝；删除后加请求不伤原始来源；新旧数据可读。
- **简历**：完整领域功能，初学者较容易掌握，可作为保守版核心。
- **五问→原理**：多对一为何改注解→对象与表关系一致；如何保留历史→增量迁移；重复关联怎么答→命令幂等；审批竞争怎么锁→同一聚合；指标为何不改parentRequest→来源时间和业务口径。

### T05 会话粒度与refresh可靠轮换

- **问题/价值/证据**：全部退出、并发轮换/重用回滚风险；[S31–S33][S59]。
- **改动范围**：AuthController、RefreshTokenService/Repository、JwtTokenProvider、User缓存、客户端logout；严格版新增auth_session/family/version。
- **依赖/前置/风险**：密码学随机token、哈希、JWT时间、锁、事务传播；客户端刷新竞争可能误判窃取，不能简单把所有并发都视作攻击。
- **最小版本**：可选token单设备退出+logout-all+老客户端兼容，明确access token残余有效期；先用DB测试修正撤销提交语义。
- **验收**：两会话退出A后B可用；全退出两者不可刷新； 他人token不能撤销；并发旧token仅一次有效轮换；重用撤销结果跨事务仍存在。严格版还需即时access拒绝测试。
- **简历**：适合安全方向追问，但不要宣传自己实现JWT算法。
- **五问→原理**：注销为何不等于JWT失效→无状态签名；只存hash为何→数据库泄露防护；重用检测为何回滚→异常传播；多标签页如何处理→单飞/轮换策略；跨实例撤权→状态版本与缓存一致性。

### T06 库存命令一致性

- **问题/价值/证据**：权限顺序、扣库与明细分离、零差额删流水、采购部分提交；[S17–S21]。
- **改动范围**：薄化PartQuantity/PurchaseOrder Controller，新增InventoryCommandService，复用Part version；PartQuantity、PartTransaction、采购状态，增加命令幂等和适当唯一约束；前端处理409与重试请求ID。
- **依赖/前置/风险**：原子性、乐观锁、deadlock、数量/退料语义。库存是高侵入任务；先测现有返回/删除语义，禁止直接改全部历史流水。
- **最小版本**：先授权、再统一事务执行“调整单条工单领用数量”；delta=0无副作用；失败库存/流水/明细一起回滚。采购审批与收货分离是后续，不在小PR中重建ERP。
- **验收**：无权限库存变化0；扣库后注入明细异常，三表不变；并发领用不超卖，失败返回409；相同requestId仅执行一次；返库数量准确；采购一项失败不得半批补库。
- **简历**：强，但若没有真实数据库测试不能写“解决超卖”；原有@Version不是个人新增。
- **五问→原理**：乐观锁为何不够→跨调用事务；库存/账本如何对账→不变量；重复点击如何处理→命令ID；为何别无限重试→冲突与负载放大；负delta含义→退料与审计追踪。

### T07 调度执行记录与恢复

- **问题/价值/证据**：重复执行、暂停/重排、调度和业务提交边界；[S13–S16]。
- **改动范围**：Job委托公开事务Service，新增schedule_execution唯一 `(schedule_id, planned_fire_time, schedule_version)`，schedule版本、补偿扫描；保持Quartz；前端可先不改。
- **前置/风险**：Quartz JobKey、misfire、时区/DST、事务代理、租约。不要只加DisallowConcurrentExecution就宣称幂等；它按JobDetail而非业务周期约束。
- **最小版本**：同计划时间重复执行只生成一次，失败可重放，有状态与错误摘要。
- **验收**：同一fireTime重复/双worker一次业务效果；停机后misfire策略符合定义；暂停不新建；变更schedule旧任务不生效；真实检查事务是否active。
- **简历**：适合调度方向；集群能力要用双实例证据。
- **五问→原理**：JobKey与业务key差异；misfire补跑策略；DST为何不等于24小时；锁释放与任务崩溃恢复；同库Quartz是否天然同事务。

### T08 权限与企业边界回归

- **问题/价值/证据**：多层鉴权依赖生命周期回调，命令检查存在顺序问题；[S17][S34–S36]。
- **改动范围**：优先改危险命令的 `findByIdAndCompany` / AuthorizationService，Controller权限和缓存边界测试，拒绝事件脱敏审计；不全库重写Repository。
- **前置/风险**：Authentication、RBAC、父子企业授权、Hibernate实体加载与native projection。上级企业合法可见不能误封；无SecurityContext的job必须显式tenant。
- **最小版本**：两企业、管理员/只读/维修角色矩阵覆盖工单、领用、报修关联。
- **验收**：越权404/403一致且0副作用；同企业无编辑权限也拒绝；缓存撤权/后台tenant都覆盖。前端无需作为权限防线。
- **简历**：有用，必须说覆盖了哪些端点，不能说“全面消除越权”。
- **五问→原理**：RBAC与数据权限区别；为何不信DTO公司ID；PostLoad何时执行；native查询绕过什么；缓存命中时授权在哪里。

### T09 工单SLA与升级

- **问题/价值/证据**：已有期限字段不是完整服务等级管理；[S08][S15][S41]。
- **改动范围**：sla_policy、工单policy快照/响应与修复deadline、sla_escalation唯一记录，Quartz扫描、Outbox通知、统计projection；前端策略与超时标签。
- **前置/风险**：Clock注入、UTC存储/企业时区展示、暂停时长、策略版本和竞态。先24×7自然时间，不实现工作日历/节假日引擎。
- **最小版本**：按优先级计算响应deadline，超时提醒一次；完成/响应后不再提醒。
- **验收**：假时钟边界、重复扫描仅一条升级、暂停/恢复、修改优先级政策、完成与扫描竞争；历史记录不被新策略追溯篡改。
- **简历**：业务直观，但首期与事件中心同时做会过载。
- **五问→原理**：deadline用哪种时间；暂停如何累计；策略改动旧单怎么办；扫描与完成竞争；为什么提醒唯一键不能只有workOrderId（多级/多策略）。

### T10 限流桶生命周期

- **问题/价值/证据**：限流已存在但Map无界；[S37]。
- **改动范围**：RateLimiterService桶容器换有界/过期策略，保留Bucket4j规则；多实例阶段才加Redis后端。无需前端变更。
- **前置/风险**：token bucket、TTL、并发Map、IP可信代理；过早淘汰会重置额度，淘汰策略不能破坏长周期限制。
- **最小版本**：单机桶容量上限、合理idle TTL、容量/拒绝率指标。
- **验收**：大量不同key输入后内存有界；窗口内请求数符合规则，429语义不变；故障时策略明确。
- **简历**：配套点，不能替代主业务亮点。
- **五问→原理**：令牌桶与滑动窗口；淘汰为何可能放大额度；按IP与用户区别；代理头可信链；何时需要分布式桶。

### T11 业务可观测性

- **问题/价值/证据**：有Actuator/Sentry但缺可证明的投递与规则指标；[S01][S24][S40]。
- **改动范围**：复用MeterRegistry，Outbox/trigger/Quartz指标、结构化eventId/companyId日志、受保护管理端点；可选Prometheus registry和本地看板，无需业务前端。
- **前置/风险**：counter/gauge/timer、直方图、基数、采样；日志不泄露token/收件人隐私，不能把健康接口200等同业务健康。
- **最小版本**：积压数、最老待发事件年龄、成功/失败/死信计数、触发去重计数。
- **验收**：注入故障后指标变化可预测，重试恢复后积压下降；0敏感凭据；指标标签有界。
- **简历**：有故障演示和诊断记录才算能力。
- **五问→原理**：counter为何不能表示队列长度；p95与平均值；高基数风险；告警为何看最老年龄；traceId与eventId的区别。

### T12 测试基线、迁移与CI

- **问题/价值/证据**：已有测试和部署，但本机无Git/Docker基线，关键数据库并发未测，CI可跳过测试；[S01][S42–S46]。
- **改动范围**：独立test profile、固定Postgres镜像版本、PR检查、迁移测试、测试产物保留、统一Surefire参数；不复用业务库、不重做前端。
- **前置/风险**：Maven生命周期、测试分层、容器资源、数据库迁移；异步测试要有可控同步点，避免sleep碰运气。
- **最小版本**：源码身份可追踪，一个真实Postgres集成测试在本机/CI可复现；测试不发外网请求。
- **验收**：新库完整迁移、旧schema升级、测试失败阻止发布；单测/集成分组、稳定重复运行；报告明确版本、总数、失败和跳过。
- **简历**：个人贡献是关键用例和门禁增强，不是“搭建整个项目CI”。
- **五问→原理**：Mock与集成的边界；为什么H2不够；测试事务为何隐藏commit问题；迁移如何回滚/前滚；CI如何避免发布绕过smoke。

## 8. Backlog 与 A/B/C 路线

### P0：开展功能前的门禁

1. T12：建立可追踪源码身份与安全测试库；保存当前可运行状态，不删 local-runtime 数据。
2. T08/F01：复现无编辑权限扣库顺序；若持久化副作用成立，最小修复“先授权+总事务”，补回归。
3. T06/F02：扣库后异常回滚实验；确认库存/明细/流水一致性。
4. T05/F05：真实事务验证重用refresh的撤销是否回滚；确认后再选择修复事务边界。

这些P0是安全/正确性验证门槛，不要求第一周完成整个库存与认证重构。若复现重大问题，宁可推迟T03性能优化，也不一边扩功能一边忽略已确认的副作用。

### P1：推荐主线

T01仪表幂等 → T02可靠事件 → T03统计正确性；T11观测和T12测试贯穿。T04作为有余力时的第四项，或替换T03的性能子任务。T07与F10仅先补测试和最小修复，不同时开启第二套大型调度改造。

### P2：进阶和可裁剪

SLA、共享限流、外部MQ、多实例Quartz、全文检索、复杂规则DSL、通用审计平台。没有测量或业务需求，不进入本期PR。

| 方案 | 范围与产出 | 学习成本/取舍 |
|---|---|---|
| A 保守增强（2–3周） | 基线+P0复现；T04关联已有工单；T03仅精度/键；单设备退出可替换T04但不同时做 | 适合先获得完整小功能闭环。暂不声称持续异常状态机、可靠通知和高并发 |
| B 推荐主线（6–10周） | T01、T02、T03三个相连增强，测试/观测配套；第4项T04仅在8–10周有余力时加入 | 最符合业务正确性+并发幂等+异步可靠性。6周只做最小版本，不做复杂冷却DSL、多渠道后台和大量SQL重构 |
| C 进阶扩展（额外4–8周以上） | 双实例验证→必要时Redis共享限流/失效→必要时MQ→SLA/调度执行账本 | 先拿实测证明跨实例问题，再承受部署、消息顺序、补偿、运维成本；保持单体，没理由强拆微服务 |

B路线独立开发粗估约30–45有效人日，读源码/补基础会拉长。若每天只能1–2小时，则6–10周不足，应先选A；不是能力不足，而是可用时间与验证成本不匹配。

## 9. 推荐方案阶段、PR与依赖

| 周 | 阶段 | 小PR | 完成门槛 |
|---|---|---|---|
| 1 | 版本、架构、单测/DB基线 | PR01 基线文档与测试命令；PR02 P0复现和最小修复（问题成立才修） | 会解释事务代理；隔离DB测试能跑，绝不指向atlas |
| 2 | 仪表最小幂等 | PR03 来源/状态schema与迁移；PR04 原子触发命令 | 两事务竞争仅一工单，历史读数编辑策略清楚 |
| 3 | 异常周期与边界 | PR05 恢复/冷却/小数阈值、DTO兼容 | 状态迁移表覆盖，乱序与重复测试通过 |
| 4 | Outbox基础 | PR06 事件与投递表；PR07 工单事务内写事件 | commit/rollback断言；不存在先发后回滚 |
| 5 | 重试、幂等与恢复 | PR08 claim/lease/backoff/dead；PR09 站内适配器与故障模拟 | 杀worker后恢复；重复消费不重复站内记录 |
| 6 | 统计口径与缓存 | PR10 精度/授权/精确键；PR11 提交后失效 | fixtures手算一致；跨企业与撤权测试 |
| 7 | 查询基线与观测 | PR12 SQL计划/可选优化；PR13 业务指标 | 未改善就保留原实现，报告真实原始数值 |
| 8 | 迁移、演示、归因 | PR14 clean install/升级回归、演示脚本、贡献清单 | README可复现，能从失败日志解释恢复过程 |
| 9–10 | 缓冲/选做 | T04关联请求或补关键P0问题 | 不在验收前硬加Redis/MQ |

依赖：PR01/02 → PR03/04 → PR05；PR06可在状态模型稳定后做 → PR07/08/09；PR10先修纯计算与key，PR11可靠失效依赖事件机制；PR12依赖固定数据与正确统计；PR14依赖所有功能验收。每个PR只引入一种核心行为，schema与业务兼容分阶段。

迁移策略：新列先可空、后台填充仅对可确定来源的数据、旧工单来源保持unknown；先检查并清点历史冲突再加唯一键；不能为了测试“干净”而删除用户数据。数据库回滚困难时用前滚修复，发布说明写明兼容旧客户端和恢复办法。

## 10. 验收实验与量化记录模板

| 实验 | 必须记录 | 接受条件（设计目标，不是已取得成绩） |
|---|---|---|
| 仪表重复/并发 | 线程数、请求ID、episode、创建数、异常数 | 每episode 1张；同请求重试0额外效果；恢复后新episode按规则触发 |
| 库存故障 | 初末库存、明细、流水、失败点、事务提交 | 拒绝/回滚0净变化；有效领用守恒；不以接口500掩盖已扣库 |
| Outbox | eventId、业务提交点、崩溃点、attempt、delivery状态 | 无未提交事件；重启后可恢复；站内幂等；外部重复边界如实说明 |
| 缓存 | exact key、企业/权限版本、查询参数、写入/失效顺序 | 与DB参考结果一致；撤权不返回缓存结果；旧读回填有明确处理 |
| SQL优化 | 数据生成seed、企业/工单/明细量、索引、EXPLAIN ANALYZE BUFFERS | 正确性不变，扫描/条数/延迟有实证收益才保留优化 |
| API压测 | JDK/DB版本、CPU/RAM/JVM堆/连接池、并发、持续时间、错误率 | 同机同配置前后对照；冷/热缓存分开；不用8条演示数据推断生产吞吐 |
| 迁移与CI | 空库/升级库版本、测试数、失败/跳过、commit | 新老数据均可读；失败阻止合并/发布；日志无凭据 |

建议性能过程：固定合成数据与请求集；预热60秒、测量180秒、每配置至少3轮；选1/5/20并发起步，先确认本机不交换内存/磁盘饱和。时间和轮数是计划，不是本轮实测。报告保留p50/p95/p99、错误率、SQL次数、DB缓冲命中/磁盘读取、GC；效果为0或变差同样需要记录。

集成测试规则沿用 `api/src/test/java/com/grash/agents.md`：服务逻辑用Mockito，Controller测路由/权限，数据库测试真实持久化/查询/约束，clear EntityManager；并发与提交测试需要独立事务，不能把外层测试回滚当成业务原子性证明。外部HTTP用本地stub或WireMock，邮件用本地假适配器，不发送给真实地址。

## 11. 原项目与个人贡献的边界

| 原作者已有 | 未来可归属于你的增强（完成后） |
|---|---|
| 工单/资产/位置/库存/PM业务、JPA实体及DTO、Web和移动界面 | 特定命令的状态、约束、权限顺序和事务修复 |
| JWT、refresh、权限、TenantAspect、企业字段 | 分设备会话/并发轮换/撤权测试与修复 |
| Caffeine、Bucket4j、线程池、邮件、Webhook签名 | 统计key/失效、可靠事件账本、重试幂等与业务指标 |
| Quartz JDBC、周期维护、通知任务 | 业务执行去重、恢复台账、SLA规则（若完成） |
| @Version库存、索引、分页、聚合SQL | 跨表原子命令、特定SQL基线与经测量的优化 |
| 单元/Controller/集成测试、Docker和CI | 缺失边界/并发/失败用例、PR门禁和可复现实验 |

依赖负责的部分也要讲清：Spring负责代理和事务拦截，PostgreSQL负责锁/约束，Quartz负责调度持久化，Caffeine负责缓存容器，Bucket4j负责令牌桶算法；你的工作是正确使用它们表达业务不变量、定义边界、处理失败并证明结果。

保留 Atlas/Grashjs/INTELLOOP 归属与许可证文件。项目内 `COMMERCIAL_LICENSE.MD` 明确列出双许可说明；本计划按用户要求保留 AGPL 与商用能力门禁，不移除LicenseService检查，不伪造license、不把Mock许可测试当成实际授权。公开修改和网络分发前应核对适用许可证条款；此报告不替代正式许可判断。[S61]

### 简历措辞：完成证据后才使用

可写成“基于 Atlas CMMS 二次开发，负责以下模块”，不能写“独立从零设计整个维护平台”。例如：

1. “为仪表异常生成工单增加异常周期状态、事务内并发控制和唯一约束，使用真实PostgreSQL双事务测试验证重复请求与持续异常的去重行为。”证据：设计图、PR、迁移SQL、并发测试及失败用例。
2. “将工单事件从内存异步派发改为事务Outbox，提供投递租约、退避重试、死信及站内幂等消费，完成进程中断恢复实验。”证据：崩溃实验日志、相同eventId重放、指标截图。不能写网络外部副作用绝对exactly-once。
3. “修复统计时长截断和缓存键冲突，明确租户/权限缓存边界，建立查询计划与冷/热缓存对照测试。”证据：固定fixtures、撤权测试、EXPLAIN和原始压测记录。只有测出收益才填X→Y，绝不预填优化百分比。

本轮474项是上游已有测试执行结果；本轮探针是审计工具；二者都不是“个人已完成上述功能”的证明。

## 12. 第一周具体行动清单

| 天 | 阅读与操作 | 当天可交付结果 |
|---|---|---|
| 第1天 | 读api/pom、application.yml、Compose、master.xml、测试基类和本报告。记录当前文件指纹，建立明确上游版本的工作副本，不覆盖运行目录 | 架构图、版本表、源码来源说明、可重复的测试命令 |
| 第2天 | 读WorkOrderController、WorkOrderService.createByUser/create/patch/changeStatus、WorkOrder/WorkOrderBase/Repository、CompanyAudit、TenantAspect | 画出事务与异步边界；执行选定单测，记录失败/跳过，不仅看BUILD SUCCESS |
| 第3天 | 读ReadingService.create/patch/processMeterTriggers、Trigger/Reading Repository、WorkOrderMeterTrigger DTO；运行外置探针 | 列出15→16、编辑同值、恢复→再异常的期望表；写第一批失败业务测试草案 |
| 第4天 | 准备可用Docker/Testcontainers环境或明确隔离测试机；读AbstractTestContainer、MeterIntegrationTest、WorkOrderIntegrationTest | 一个空测试库迁移+提交回滚用例可运行；检查URL不包含现有atlas库 |
| 第5天 | 读PartQuantityController.patch与PartService.consumePart；做两角色/故障注入回归；读refresh.rotate异常事务 | P0测试结果。**首个最小代码修改优先是已确认链路“授权先于扣库”的Service命令边界与回归测试**；如DB测试环境未就绪，先提交纯逻辑测试，不假装事务修复完成 |
| 第6天 | 读CacheKeyUtils、WOAnalyticsController.getCompleteStats、WorkOrder.getAverageAge；定义秒/分钟口径、缓存键和权限边界 | 第二个小PR：统计纯计算/键修复与测试；不装Redis，不改一堆报表 |
| 第7天 | 总结测试和风险，选择T01最小语义；设计trigger来源/episode表、锁顺序和唯一约束，评审迁移兼容性 | 下一周PR拆分、两事务测试设计、10分钟可讲清的业务闭环说明 |

如果第一周独立测试环境仍未解决，第二周先完成环境和事务基础；不应一边不懂代理/commit，一边直接开发Outbox和复杂状态机。

## 13. 核心问题的最终回答

第一次系统性学习 Java 工程，我建议优先选择：

1. **仪表异常触发去重与恢复状态**：有真实Issue和可复现旧行为，业务独特且不是通用商城套路；把状态机、并发、幂等和数据约束连在一起。先做小版本，再加持续异常。
2. **可靠工单事件与通知**：复用现有通知能力，只补最有价值的事务缺口。用PostgreSQL Outbox即可展示可靠重试和恢复，避免MQ安装掩盖真正问题。
3. **统计准确性与缓存一致性**：可从简单的精度/键修复起步，再进入授权、提交后失效和SQL测量；学习跨度较平缓，也能展示实证而非口号。

顺序上可先做统计纯计算的小PR练手，再攻仪表状态机和Outbox。若只剩2–3周，改选“报修关联已有工单+统计小修复+测试”，比半成品分布式系统更适合面试。P0库存/会话问题一旦经集成测试成立，应先做最小修复，再推进这三项。项目价值来自你能解释并证明的边界，而不是新增依赖的数量。

## 附录 A：源码证据索引

以下索引由本地文件自动定位生成。方法入口行用于导航，正文所述行为需结合该方法完整实现及相关实体/Repository阅读。

| 编号 | 实际源码路径与行号 | 类 / 方法 / 内容 |
|---|---|---|
| S01 | [api/pom.xml:4](../api/pom.xml#L4) | Maven parent、依赖与测试插件 |
| S02 | [api/src/main/resources/application.yml:1](../api/src/main/resources/application.yml#L1) | Spring datasource、JPA、Liquibase、Quartz配置 |
| S03 | [api/src/main/java/com/grash/service/ReadingService.java:57](../api/src/main/java/com/grash/service/ReadingService.java#L57) | ReadingService.create / patch / processMeterTriggers，57–192 |
| S04 | [api/src/main/java/com/grash/model/WorkOrderMeterTrigger.java:27](../api/src/main/java/com/grash/model/WorkOrderMeterTrigger.java#L27) | WorkOrderMeterTrigger字段，27–61 |
| S05 | [api/src/main/java/com/grash/repository/ReadingRepository.java:12](../api/src/main/java/com/grash/repository/ReadingRepository.java#L12) | ReadingRepository查询 |
| S06 | [api/src/main/java/com/grash/service/WorkOrderService.java:113](../api/src/main/java/com/grash/service/WorkOrderService.java#L113) | WorkOrderService.create，113–147 |
| S07 | [api/src/main/java/com/grash/service/WorkOrderService.java:201](../api/src/main/java/com/grash/service/WorkOrderService.java#L201) | WorkOrderService.update / patch，201–268 |
| S08 | [api/src/main/java/com/grash/service/WorkOrderService.java:832](../api/src/main/java/com/grash/service/WorkOrderService.java#L832) | WorkOrderService.changeStatus，832–937 |
| S09 | [api/src/main/java/com/grash/controller/WorkOrderController.java:118](../api/src/main/java/com/grash/controller/WorkOrderController.java#L118) | WorkOrderController.create / patch / changeStatus |
| S10 | [api/src/main/java/com/grash/model/abstracts/WorkOrderBase.java:1](../api/src/main/java/com/grash/model/abstracts/WorkOrderBase.java#L1) | WorkOrderBase公共字段与关联 |
| S11 | [api/src/main/java/com/grash/model/WorkOrder.java:36](../api/src/main/java/com/grash/model/WorkOrder.java#L36) | WorkOrder来源、状态、审计、权限与getAverageAge |
| S12 | [api/src/main/java/com/grash/repository/WorkOrderRepository.java:72](../api/src/main/java/com/grash/repository/WorkOrderRepository.java#L72) | WorkOrderRepository企业时间范围与聚合查询 |
| S13 | [api/src/main/java/com/grash/service/PreventiveMaintenanceService.java:70](../api/src/main/java/com/grash/service/PreventiveMaintenanceService.java#L70) | PreventiveMaintenanceService.create |
| S14 | [api/src/main/java/com/grash/service/PreventiveMaintenanceService.java:136](../api/src/main/java/com/grash/service/PreventiveMaintenanceService.java#L136) | createWorkOrderFromPreventiveMaintenance / triggerWorkOrder |
| S15 | [api/src/main/java/com/grash/service/ScheduleService.java:74](../api/src/main/java/com/grash/service/ScheduleService.java#L74) | ScheduleService.scheduleWorkOrder / reScheduleWorkOrder |
| S16 | [api/src/main/java/com/grash/job/WorkOrderCreationJob.java:20](../api/src/main/java/com/grash/job/WorkOrderCreationJob.java#L20) | WorkOrderCreationJob.executeInternal |
| S17 | [api/src/main/java/com/grash/controller/PartQuantityController.java:72](../api/src/main/java/com/grash/controller/PartQuantityController.java#L72) | PartQuantityController.patchWorkOrder / patch / delete，72–241 |
| S18 | [api/src/main/java/com/grash/service/PartService.java:146](../api/src/main/java/com/grash/service/PartService.java#L146) | PartService.consumePart / restockPart，146–214 |
| S19 | [api/src/main/java/com/grash/model/Part.java:69](../api/src/main/java/com/grash/model/Part.java#L69) | Part.quantity / @Version |
| S20 | [api/src/main/java/com/grash/controller/PurchaseOrderController.java:154](../api/src/main/java/com/grash/controller/PurchaseOrderController.java#L154) | PurchaseOrderController.respond |
| S21 | [api/src/main/java/com/grash/service/PurchaseOrderService.java:33](../api/src/main/java/com/grash/service/PurchaseOrderService.java#L33) | PurchaseOrderService.create / update / save |
| S22 | [api/src/main/java/com/grash/service/NotificationService.java:41](../api/src/main/java/com/grash/service/NotificationService.java#L41) | NotificationService.create / createMultiple |
| S23 | [api/src/main/java/com/grash/service/WebhookDispatchService.java:41](../api/src/main/java/com/grash/service/WebhookDispatchService.java#L41) | WebhookDispatchService.dispatchWebhook / sendWebhook |
| S24 | [api/src/main/java/com/grash/configuration/AsyncConfig.java:18](../api/src/main/java/com/grash/configuration/AsyncConfig.java#L18) | AsyncConfig.getAsyncExecutor / getAsyncUncaughtExceptionHandler |
| S25 | [api/src/main/java/com/grash/configuration/CacheConfig.java:19](../api/src/main/java/com/grash/configuration/CacheConfig.java#L19) | CacheConfig.cacheManager / buildSpec |
| S26 | [api/src/main/java/com/grash/utils/CacheKeyUtils.java:6](../api/src/main/java/com/grash/utils/CacheKeyUtils.java#L6) | CacheKeyUtils.roundToNearest20Minutes / dateRangeKey |
| S27 | [api/src/main/java/com/grash/controller/analytics/WOAnalyticsController.java:50](../api/src/main/java/com/grash/controller/analytics/WOAnalyticsController.java#L50) | WOAnalyticsController.getCompleteStats，50–81 |
| S28 | [api/src/main/java/com/grash/service/WorkOrderService.java:504](../api/src/main/java/com/grash/service/WorkOrderService.java#L504) | getLaborCostAndTime / getAdditionalCost / getPartCost |
| S29 | [api/src/main/java/com/grash/service/RequestService.java:372](../api/src/main/java/com/grash/service/RequestService.java#L372) | RequestService.approve；createWorkOrderFromRequest见194行 |
| S30 | [api/src/main/java/com/grash/model/Request.java:42](../api/src/main/java/com/grash/model/Request.java#L42) | Request.workOrder / preRemove / canBeViewedBy |
| S31 | [api/src/main/java/com/grash/controller/AuthController.java:182](../api/src/main/java/com/grash/controller/AuthController.java#L182) | AuthController.refresh / logout |
| S32 | [api/src/main/java/com/grash/service/RefreshTokenService.java:41](../api/src/main/java/com/grash/service/RefreshTokenService.java#L41) | RefreshTokenService.createRefreshToken / rotate / revokeAllForUser |
| S33 | [api/src/main/java/com/grash/security/JwtTokenProvider.java:49](../api/src/main/java/com/grash/security/JwtTokenProvider.java#L49) | JwtTokenProvider.createToken / getAuthentication |
| S34 | [api/src/main/java/com/grash/model/abstracts/CompanyAudit.java:29](../api/src/main/java/com/grash/model/abstracts/CompanyAudit.java#L29) | CompanyAudit.beforePersist / afterLoad |
| S35 | [api/src/main/java/com/grash/aspect/TenantAspect.java:38](../api/src/main/java/com/grash/aspect/TenantAspect.java#L38) | TenantAspect.validateTenant / validateFieldElement |
| S36 | [api/src/main/java/com/grash/utils/Helper.java:413](../api/src/main/java/com/grash/utils/Helper.java#L413) | Helper.resolveCompanyId |
| S37 | [api/src/main/java/com/grash/service/RateLimiterService.java:18](../api/src/main/java/com/grash/service/RateLimiterService.java#L18) | RateLimiterService桶Map；113行起resolve方法 |
| S38 | [api/src/main/java/com/grash/service/CacheService.java:24](../api/src/main/java/com/grash/service/CacheService.java#L24) | CacheService.evictUserFromCache / putUserInCache / getUserFromCache |
| S39 | [api/src/main/java/com/grash/service/CustomSequenceService.java:27](../api/src/main/java/com/grash/service/CustomSequenceService.java#L27) | CustomSequenceService.getOrCreateCustomSequence / getNextWorkOrderSequence |
| S40 | [api/src/main/java/com/grash/configuration/QuartzConfig.java:35](../api/src/main/java/com/grash/configuration/QuartzConfig.java#L35) | QuartzConfig.sentryQuartzSchedulerCustomizer / SentryJobListener |
| S41 | [api/src/main/java/com/grash/model/enums/workflow/WFMainCondition.java:3](../api/src/main/java/com/grash/model/enums/workflow/WFMainCondition.java#L3) | WFMainCondition已有事件枚举 |
| S42 | [.github/workflows/main-ci.yml:1](../.github/workflows/main-ci.yml#L1) | main-ci触发、test、selfhost-smoke、发布依赖 |
| S43 | [api/src/test/java/com/grash/integration/AbstractTestContainer.java:15](../api/src/test/java/com/grash/integration/AbstractTestContainer.java#L15) | AbstractTestContainer：容器、测试库、属性覆盖；同目录其他IntegrationTest |
| S44 | [docker-compose.yml:1](../docker-compose.yml#L1) | Compose服务及镜像环境变量 |
| S45 | [api/Dockerfile:1](../api/Dockerfile#L1) | 后端容器构建/运行入口 |
| S46 | [api/src/main/resources/db/master.xml:1](../api/src/main/resources/db/master.xml#L1) | Liquibase迁移入口 |
| S47 | [api/src/main/java/com/grash/dto/workOrder/WorkOrderPostDTO.java:13](../api/src/main/java/com/grash/dto/workOrder/WorkOrderPostDTO.java#L13) | WorkOrderPostDTO extends WorkOrder |
| S48 | [api/src/main/java/com/grash/service/WorkOrderService.java:1091](../api/src/main/java/com/grash/service/WorkOrderService.java#L1091) | WorkOrderService.createByUser |
| S49 | [api/src/main/java/com/grash/controller/ReadingController.java:52](../api/src/main/java/com/grash/controller/ReadingController.java#L52) | ReadingController.create / patch |
| S50 | [api/src/main/java/com/grash/controller/PreventiveMaintenanceController.java:66](../api/src/main/java/com/grash/controller/PreventiveMaintenanceController.java#L66) | PreventiveMaintenanceController.triggerWorkOrder / create |
| S51 | [api/src/main/java/com/grash/model/PartQuantity.java:20](../api/src/main/java/com/grash/model/PartQuantity.java#L20) | PartQuantity实体关联与数量；getCost见43行 |
| S52 | [api/src/main/java/com/grash/exception/RestExceptionHandlerController.java:89](../api/src/main/java/com/grash/exception/RestExceptionHandlerController.java#L89) | handleOptimisticLocking返回409 |
| S53 | [api/src/main/java/com/grash/service/EmailService2.java:109](../api/src/main/java/com/grash/service/EmailService2.java#L109) | EmailService2.sendMessageUsingThymeleafTemplate |
| S54 | [api/src/main/java/com/grash/repository/WorkOrderRepository.java:98](../api/src/main/java/com/grash/repository/WorkOrderRepository.java#L98) | 成本预聚合SQL；repair time见182行 |
| S55 | [api/src/main/java/com/grash/controller/RequestController.java:85](../api/src/main/java/com/grash/controller/RequestController.java#L85) | RequestController.approve / cancel |
| S56 | [api/src/main/java/com/grash/service/WorkOrderHistoryService.java:23](../api/src/main/java/com/grash/service/WorkOrderHistoryService.java#L23) | WorkOrderHistoryService历史查询与持久化 |
| S57 | [api/src/main/java/com/grash/service/DemoDataService.java:58](../api/src/main/java/com/grash/service/DemoDataService.java#L58) | AFTER_COMMIT监听，仅示例数据链路 |
| S58 | [api/src/main/java/com/grash/model/User.java:152](../api/src/main/java/com/grash/model/User.java#L152) | User.canSeeAnalytics |
| S59 | [api/src/main/java/com/grash/repository/RefreshTokenRepository.java:13](../api/src/main/java/com/grash/repository/RefreshTokenRepository.java#L13) | RefreshTokenRepository查询与批量撤销 |
| S60 | [api/src/main/java/com/grash/repository/CustomSequenceRepository.java:10](../api/src/main/java/com/grash/repository/CustomSequenceRepository.java#L10) | CustomSequenceRepository.findByCompanyId，无锁声明 |
| S61 | [COMMERCIAL_LICENSE.MD:10](../COMMERCIAL_LICENSE.MD#L10) | 双许可说明；同时保留根LICENSE |

## 附录 B：本次执行证据

- `local-runtime/logs/backend-audit-tests.log`：首次离线测试缺运行器依赖。
- `local-runtime/logs/backend-audit-tests-online.log`：指定474项通过，主/测试源码编译成功。
- `local-runtime/logs/audit-probes.log`：4项局部代码行为复现，明确不覆盖数据库和并发。
- `local-runtime/logs/audit-db-metadata.log`：只读数据库版本、迁移数、索引、request/part_quantity约束及8张工单计数。
- `docs/audit-evidence/CmmsAuditProbe.java`：外置探针源代码；不是业务测试套件自动发现的测试。
- `docs/audit-evidence/source-fingerprints.json`：被审计生产Java、配置及测试源码指纹，不包含local-runtime凭据。
- `docs/audit-evidence/github-issues.json`：指定Issue标题、状态、时间与URL的核对摘要。
- `docs/audit-evidence/test-summary.txt`、`probe-results.txt`、`database-metadata.txt`：本轮结果快照，便于运行日志随后更新时保留原始结论。
- `docs/audit-evidence/run-audit-probes.ps1`：先执行报告中的指定Maven测试，再设置JAVA_HOME为JDK17并运行此脚本，可重新编译/执行外置探针；探针仅使用Mock协作者，不访问业务数据库。

本轮未运行：完整容器栈、全套集成/安全/并发测试、真实邮件Webhook投递、多实例Quartz、全量性能压测、全部业务前端回归。没有这些证据的能力与收益在本报告中均为待验证或计划。
