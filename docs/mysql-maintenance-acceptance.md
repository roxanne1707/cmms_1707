# CMMS：MySQL 与 MyBatis-Plus 主线适配验收

本轮将 MySQL 接入实际 CMMS 业务，而不再只停留在独立练习表。一个应用实例只选择一种数据库；工单、仪表、备件、事件和审计仍在同一数据源、同一本地事务中。PostgreSQL 保留为默认配置，原部署及业务数据没有搬迁。

## 范围与隔离

- 工作仓库：`cmms-fork`，分支 `feat/mysql-maintenance`。之前的增强已先保存为提交 `85c47644`；另有源码快照及逐文件 SHA-256 清单。
- MySQL 新实例：API `127.0.0.1:28189`，前端预览 `127.0.0.1:3389`，数据库 `127.0.0.1:23306/cmms_mysql_lab`。
- Redis 使用专属 DB 1 与缓存命名空间；RabbitMQ 使用 `cmms-mysql` vhost。回归测试使用随机命名的新库，与演示库也分开。
- 原 PostgreSQL 部署和增强环境的端口、数据库、配置没有切换。没有运行未知安装脚本，也没有发送真实邮件、推送或 Webhook。
- 凭据保存在 Git 忽略的 `local-runtime/mysql-maintenance/access.json`；普通维护账号在 `demo-access.json`。不要提交这些文件。

## 实际验收结果

| 验证 | 结果 |
|---|---|
| MySQL 全量（索引优化前） | 通过 2147；失败 0；错误 0；跳过 25 |
| PostgreSQL 全量 | 通过 2172；失败 0；错误 0；跳过 0 |
| MySQL 最终关键链路与打包 | 通过 59；失败 0；错误 0；跳过 0 |
| 前端请求及会话测试 | 37 项通过 |
| HTTP 业务验收 | 通过，具体断言见证据文件 |
| MySQL 独立实例 | 已启动；站点与反向代理检查通过 |
| 最小权限 | 运行账号 TRUNCATE 空探针表被拒绝 |

PostgreSQL 测试全部通过后，最初的默认产物打包因 Windows 文件占用失败；没有终止旧服务。随后使用独立名称 `app-mysql.jar` 完成打包，最终关键回归及打包命令成功。启动脚本明确使用独立产物。可执行包由 `mvn package -Dcmms.artifact-name=app-mysql` 构建。

全量 MySQL 回归与最终 59 项回归属于不同轮次，不能相加当作独立测试数量。最终一轮包含新增覆盖索引的建库、JPA/MP 查询、仪表库存事务、SLA、消息与会话安全。

## 本轮完成的代码工作

| 改造 | 实际内容与理由 |
|---|---|
| MySQL 完整新安装基线 | 单独的 Liquibase master、完整表结构、约束、索引、货币参考数据、序列表和业务触发器。保留 PostgreSQL 的历史 changelog，不能将两个 master 混用。 |
| 保留 JPA 核心业务 | 设备、工单、备件、用户等实体继续由 JPA 管理；没有机械替换整个持久层。 |
| 数据库方言边界 | `DatabasePlatform` 集中选择有差异的 SQL；事务锁由 PostgreSQL advisory lock 对应到 MySQL InnoDB 锁记录；没有使用连接级 `GET_LOCK` 代替事务锁。 |
| 维修台账 MyBatis-Plus | 保留已有台账模块，增加数据库标识、分页方言和日期计算适配；与 JPA 基线比较查询数量和工单 ID，保留企业过滤条件。 |
| MyBatis-Plus 写操作 | 将真实的通知失败补投审计接入 `BaseMapper`，通过同一事务提交补投状态、Outbox 和审计；异常时一并回滚。不开放无权限限制的通用 CRUD 接口。 |
| 仪表与备件并发 | 保留异常周期状态、幂等键、库存检查和锁顺序；验证 MySQL 上的并发扣库、失败回滚和工单编号分配。 |
| RabbitMQ 可靠事件 | 适配 Outbox、租约、重试、投递、消费回执和补投 SQL，保留发布确认、手动 ACK、幂等与失败恢复。真实本地 RabbitMQ 参与测试。 |
| 时间、UUID 和 ID | UTC `DATETIME(6)`，JDBC/MyBatis UUID 转换，显式微秒时间；通知 JDBC ID 预留与 Hibernate 分配段互不重叠，使用跨多个分配段的回归测试验证。 |
| 搜索与统计 | 8 条原生统计查询显式提供 MySQL SQL；普通 LIKE 搜索使用适当的 MySQL 排序规则。不是对 SQL 文本做任意自动翻译。 |
| 缓存与会话隔离 | 数据库事务中的设备统计版本继续决定缓存新鲜度；增加部署命名空间。会话撤销、Refresh Token 复用及并发退出测试在 MySQL 上执行。 |
| Quartz | 配置 JDBC 标准 delegate 与一致的 `QRTZ_` 表名；新实验库单独保存调度数据，不连接原调度表。 |
| 新环境初始化 | 空安装管理员账号允许通过配置生成；已有账号不会被覆盖。运行账号仅有 DML 权限，数据库结构由单独迁移账号管理。 |

## 本轮验证中实际修复的问题

1. PostgreSQL 的 `OFFSET … LIMIT` 顺序不能直接用于对应 MySQL 查询，调整为两边都可用的分页顺序。
2. 原生 `EXISTS` 投影在 MySQL 下的数值类型与 Boolean 不一致；改为明确的数值投影和布尔接口包装，保留原许可证/使用量检查。
3. PostgreSQL `UUID`、`ON CONFLICT`、时间间隔、聚合 FILTER、advisory lock、`FOR UPDATE OF` 不能照搬，逐处显式适配。
4. MySQL 秒级当前时间与微秒级事件到期时间混用，曾造成刚创建的 READY 消息不能领取；统一精度后原失败用例通过。
5. MyBatis 查询枚举、时间投影以及 UUID 审计写入需要匹配实际驱动返回类型。
6. 多个隔离测试库可能产生相同设备 ID 和统计版本，不能共享无命名空间的缓存键；测试使用各自库名作为缓存命名空间。
7. 旧安全测试夹具强制 PostgreSQL，不能代表 MySQL 失败或通过；扩展夹具后保留原有事务、复用、并发撤销断言，实际重跑。

`useAffectedRows=true` 是 MySQL 幂等插入返回值语义的一部分，不可随意删除。[Connector/J 官方说明](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-connection.html)说明了它对受影响行数的作用。时间精度选择对应 [MySQL 微秒时间说明](https://dev.mysql.com/doc/refman/8.0/en/fractional-seconds.html)。


## MySQL 查询计划与性能证据

单机 MySQL 8.0.33，10 万条合成工单，两个企业；小设备 100 条、大设备 49,900 条。使用项目实际设备概览聚合 SQL，每种场景取 5 次 `EXPLAIN ANALYZE` 根节点耗时的中位数。不是 HTTP 压测。

| 场景 | 原优化器计划 ms | 全表扫描诊断对照 ms | 覆盖索引与定向提示 ms |
|---|---:|---:|---:|
| 小设备 | 0.192 | 38.4 | 0.122 |
| 大设备 | 75.6 | 60.1 | 42.2 |

实际发现：仅添加覆盖索引不足以让这个版本的优化器选择合适计划，排除公司单列索引后又可能误选设备外键单列索引。因此最终只在固定的 MySQL 设备概览聚合上使用 `FORCE INDEX(ix_work_order_overview_cover)`，配合独立 Liquibase changeset；原台账分页索引仍保留。该查询所需列均由覆盖索引提供。

这是同一合成数据上的实测，不代表任何线上提升比例。新增索引有存储和写入维护成本，本轮未量化写吞吐变化。数据库升级、查询字段或索引变化时必须重测这条提示；不要将提示复制到其他业务 SQL。完整计划和每次样本见 `evidence/mysql-adaptation/mysql-sql-results.json`。

新实验环境首次启动还发现生成密钥的 URL-safe Base64 与原 JWT 解码器要求不符；已保留副本、修正新实验密钥编码，并在启动脚本增加校验。原部署密钥没有修改。

## 验收边界

- 这是独立的新安装 MySQL 路线，不是原 PostgreSQL 业务数据的在线迁移。旧数据转换、切换、双写、回滚切流均没有执行。
- 本轮使用真实本地 MySQL/PostgreSQL/Redis/RabbitMQ；测试不是用 H2 模拟数据库。MySQL 专属 Docker、Linux 部署及线上集群故障尚未在本轮验证，不能据此宣称通过。
- 全量测试通过不等于不存在其他缺陷。25 项使用 PostgreSQL 特有夹具的测试在 MySQL 轮次明确跳过，在 PostgreSQL 轮次执行；不能将跳过计为 MySQL 通过。
- `EXPLAIN ANALYZE` 的强制全表扫描与优化器选择对比，只说明给定数据分布下的查询计划，不等于本轮给产品带来相同比例的性能提升，也不代表 HTTP 吞吐量或生产容量。
- MySQL 排序规则的重音/大小写匹配并非 PostgreSQL `unaccent` 对所有 Unicode 字符的完全等价转换。
- 运行账号禁止 DDL/TRUNCATE；人工恢复数据库或重用 ID 时应切换缓存命名空间。不能依靠 MySQL 行触发器覆盖 TRUNCATE 或所有级联删除行为。
- 许可证检查、作者归属和原有套餐边界保留；没有通过新建 MySQL 路线绕过付费功能。集成测试中的合成业务数据不等于新部署获得商业授权。

## 启动、回归与回滚

- 空库初始化：`scripts/provision-mysql-maintenance.py --mysql <mysql客户端> --client-config <仓库外管理员配置>`，只连接本机 23306；拒绝已存在的实验库或凭据文件，生成标准 Base64 JWT 密钥。运行前需准备隔离 MySQL、Redis 26379、RabbitMQ 25672，并创建 `cmms-mysql` vhost 和本地测试用户权限。
- 新实例启动脚本：`scripts/start-mysql-maintenance.ps1`；加 `-Frontend` 启动 3389 预览。脚本遇到已占用端口会停止操作，不会杀掉已有进程。
- MySQL 回归：`scripts/test-mysql-maintenance.ps1 -Maven <mvn> -Settings <settings.xml> -CredentialFile <仓库外测试账号JSON>`。脚本只接受 `cmms_mysql_test` 和本机 23306 专用库。
- PostgreSQL 回归：原 `scripts/test-maintenance.ps1`，继续使用独立 25432 测试数据库。
- SQL 计划验证：`scripts/benchmark-mysql-maintenance.py --mysql <mysql客户端> --client-config <仓库外客户端配置>`，只创建新的 `cmms_mysql_perf_*` 库，不清空任何原有表。
- 测试数据库保留用于诊断。不要将全量 Maven 日志或 Surefire XML 直接公开，它们可能包含测试连接参数。仓库证据仅保存脱敏汇总。
- 回退本轮功能只需停止新实例、继续使用原部署。不要将 MySQL changelog 指向已有 PostgreSQL 数据库，也不要删除原部署来“完成迁移”。

## 贡献归属

Atlas CMMS 的设备管理、工单、JPA 实体体系和产品基础属于原项目。仪表幂等、可靠通知、Redis 概览、维修台账等前期增强在基线提交中已经存在。

本轮新增贡献是：实际业务的 MySQL 方言与迁移适配、双数据库事务验证、补投审计的 MyBatis-Plus 写路径、运行隔离与验证证据。它们都是在原项目基础上的二次开发；不能把整个 Atlas CMMS 或原作者功能写成个人原创。

## 关键源码定位

| 内容 | 路径与行号 |
|---|---|
| 数据库方言和事务锁 | [api/src/main/java/com/grash/database/DatabasePlatform.java:13](../api/src/main/java/com/grash/database/DatabasePlatform.java#L13) |
| MySQL 配置 | [api/src/main/resources/application-mysql.yml:2](../api/src/main/resources/application-mysql.yml#L2) |
| 建库和索引 | [api/src/main/resources/db/mysql/master.xml:2](../api/src/main/resources/db/mysql/master.xml#L2) |
| 概览与覆盖索引 | [api/src/main/java/com/grash/service/query/AssetWorkOrderOverviewRepository.java:43](../api/src/main/java/com/grash/service/query/AssetWorkOrderOverviewRepository.java#L43) |
| MP 方言配置 | [api/src/main/java/com/grash/configuration/MybatisReadConfig.java:16](../api/src/main/java/com/grash/configuration/MybatisReadConfig.java#L16) |
| 台账 SQL | [api/src/main/resources/mybatis/query/MaintenanceLedgerMapper.xml:3](../api/src/main/resources/mybatis/query/MaintenanceLedgerMapper.xml#L3) |
| 补投事务审计 | [api/src/main/java/com/grash/event/EventReplayService.java:85](../api/src/main/java/com/grash/event/EventReplayService.java#L85) |
| 审计实体 | [api/src/main/java/com/grash/writemodel/NotificationReplayAudit.java:8](../api/src/main/java/com/grash/writemodel/NotificationReplayAudit.java#L8) |
| 业务集成测试 | [api/src/test/java/com/grash/integration/MaintenanceJpaIntegrationTest.java:54](../api/src/test/java/com/grash/integration/MaintenanceJpaIntegrationTest.java#L54) |
| RabbitMQ 测试 | [api/src/test/java/com/grash/event/ReliableEventsPostgresIntegrationTest.java:46](../api/src/test/java/com/grash/event/ReliableEventsPostgresIntegrationTest.java#L46) |
| 会话测试 | [api/src/test/java/com/grash/security/SessionRegistryPostgresIntegrationTest.java:27](../api/src/test/java/com/grash/security/SessionRegistryPostgresIntegrationTest.java#L27) |

## 运行版本与证据

mysql-connector-j-9.7.0.jar, mybatis-plus-core-3.5.18.jar, hibernate-core-6.6.53.Final.jar, spring-data-jpa-3.5.13.jar, liquibase-core-5.0.3.jar, quartz-2.5.2.jar

证据目录：[evidence/mysql-adaptation](evidence/mysql-adaptation)。包含分轮回归汇总、真实 HTTP 断言、运行与权限检查、SQL 执行计划；不含账号密码、JWT、完整测试日志或原业务数据。
