# Atlas CMMS 最终收尾验收（2026-11-14）

本轮指定的四项收尾已完成本地实现和验收：仪表策略 Web 页面、可执行启动产物、MySQL 完整回归及双数据库 CI 定义、统一验收文档。验收范围为 `cmms-fork` 的独立测试环境；原 `cmms` 目录、数据库和部署配置没有迁移、清理或覆盖。此前尚未执行的远端 CI、隔离容器与受控通知协议，现已在 [补充验收](maintenance-remote-verification.md) 中完成；本页其余本地数据保留原轮次。真实外部服务商到达仍未验证。

## 1. 完成内容与源码入口

| 项目 | 本轮实际完成 | 主要代码与证据 |
|---|---|---|
| 仪表策略页面 | 新建、编辑 `PER_EPISODE` / `ONCE`、异常等待秒数、整数阈值边界、列表摘要与重新打开回显；403 保留表单，允许修正后重试；按仪表编辑权限显示入口。 | `frontend/src/utils/meterTriggerPolicy.ts:5`、`frontend/src/content/own/Meters/AddTriggerModal.tsx`、`EditTriggerModal.tsx`、`MeterDetails.tsx:362`；`scripts/test-meter-policy.cjs`。 |
| 真实后端契约 | 两种数据库验证 ONCE/60 保存、旧客户端省略策略仍保留 ONCE、等待改为 120、非法负等待整体回滚；不修改许可证检查。 | `api/src/test/java/com/grash/integration/MaintenanceJpaIntegrationTest.java` 的 `meterPolicyEditRoundTripsWithoutResettingOmittedPolicy()`。 |
| 启动产物 | 构建唯一命名的 Boot JAR，检查 Manifest、BOOT-INF 与 SHA-256 后更新本地指针；PG/MySQL 启动脚本统一使用；不会覆盖运行中的旧 JAR。原错误的薄 `app.jar` 已另存备份，再从验证后的可执行包恢复兼容路径。 | `scripts/build-maintenance-artifact.ps1`、`maintenance-artifact.ps1:2`；运行包和旧备份不提交 Git。 |
| 启停安全 | 记录 PID、产物路径、端口、进程创建时间，停止时逐项比对；修复 PowerShell 7 JSON 时间自动解析导致的时区丢失。28090 临时实例实际启动后由同一脚本停止，其他实例保持运行。 | `scripts/start-maintenance-lab.ps1`、`start-mysql-maintenance.ps1`、`stop-maintenance-lab.ps1`、`maintenance-artifact.ps1:32`。 |
| 启动连接隔离 | 复现同一 PowerShell 会话先 MySQL 再 PostgreSQL 时遗留迁移连接；两套启动器显式设置运行/迁移 URL、身份、方言和 changelog，移除外部 Spring 配置重定向与 PG 的遗留初始化账号。双向切换实际启动均验证。 | `scripts/maintenance-environment.ps1`、`test-maintenance-environment.ps1`；[启动隔离](evidence/maintenance-finalization/launcher-isolation-results.json)。 |
| MySQL 测试 | 移植此前跳过的 25 项：领域事务 11、附件/调度可靠性 4、查询/缓存 10。保留业务断言；真实 MySQL 生成 ID、READ COMMITTED、显式外键和生产表定义。 | `api/src/test/java/com/grash/integration/JdbcLab.java`、`DomainReliabilityIntegrationTest`、`ExtraMaintenanceReliabilityIntegrationTest`、`query/QueryInfrastructureIntegrationTest`。 |
| MySQL 缓存版本缺陷 | 复现设备跨企业移动或删除重建后 epoch 复用，追加独立历史计数表及两条 BEFORE 触发器。原 summary 的两条外键和已有业务触发器保留。 | `api/src/main/resources/db/mysql/epoch-counter.sql:1`、`epoch-counter-triggers.sql:1`、`master.xml` 的新 changeset；`assetCompanyRoundTripAndRecreationNeverReuseVersions()`。 |
| 监控与限流隔离 | 实际启动后发现已认证的监控抓取消耗匿名业务配额，二者一起 429。关闭限流 Filter 的重复 Servlet 自动注册，只保留在业务安全链中，监控使用独立安全链；业务限流、监控认证均继续生效。 | `api/src/main/java/com/grash/configuration/WebSecurityConfig.java:53`；`MaintenanceJpaIntegrationTest.metricsScrapesAndBusinessQuotaAreIsolatedOnTheRealServer()`；修复前后 HTTP 证据。 |
| CI | PostgreSQL/MySQL 独立矩阵、完整回归零跳过门禁、可执行包检查、前端类型/构建/51 项测试。只上传脱敏统计；不上传原始 Surefire XML。 | `.github/workflows/maintenance-tests.yml`、`scripts/summarize-maintenance-tests.py`、`test-maintenance-summary.py`。 |
| 验收文档 | 以本报告及清单为当前状态入口；原审计、设计和历史性能数据保留并标记轮次。 | [验收清单](maintenance-acceptance-checklist.md)、[历史 MySQL 适配](mysql-maintenance-acceptance.md)、[历史完整验收](maintenance-completion-acceptance.md)。 |

## 2. 当前测试结果

| 验证 | 本轮结果 | 可审查证据 |
|---|---|---|
| MySQL 全量后端回归 | **2,175 通过，0 失败、0 错误、0 跳过** | [MySQL 摘要](evidence/maintenance-finalization/mysql-summary.json) |
| PostgreSQL 全量后端回归 | **2,175 通过，0 失败、0 错误、0 跳过** | [PostgreSQL 摘要](evidence/maintenance-finalization/postgresql-summary.json) |
| 前端类型检查与生产构建 | 退出码 0；构建保留类型检查和压缩 | [前端结果](evidence/maintenance-finalization/frontend-results.json) |
| 前端脚本测试 | 25 客户端 + 12 会话连接 + 14 仪表策略 = **51 通过** | 同上；[原始日志哈希](evidence/maintenance-finalization/private-log-hashes.json) |
| 产物与进程时间校验 | **9 项通过**：完整 Boot 包、错误哈希、路径越界、薄包、缺失指针；UTC 字符串/DateTime/偏移字符串/缺失时区 | [产物校验](evidence/maintenance-finalization/artifact-guard-results.json) |
| 启动器环境隔离 | **3 组通过**：继承连接/配置重定向、PG → MySQL、MySQL → PG；并实测两套 API 启动 | [环境守卫](evidence/maintenance-finalization/environment-guard-results.json)、[实际切换](evidence/maintenance-finalization/launcher-isolation-results.json) |
| CI 统计门禁自测 | **6 项通过**，拒绝跳过、失败、不完整、累计不符和拼接日志 | [统计门禁](evidence/maintenance-finalization/summary-gate-results.json) |
| 浏览器仪表策略 | **8 组场景通过**；真实页面、真实测试登录，策略写操作用合成接口夹具；桌面及手机布局已看图确认 | [浏览器结果](evidence/maintenance-finalization/meter-browser-results.json) |
| 监控/业务实际 HTTP 隔离 | 修复前两者均 429；修复后连续认证抓取不消耗业务配额，业务仍可触发 429，随后认证监控仍 200，未认证监控仍 401 | [修复前](evidence/maintenance-finalization/metrics-isolation-before.json)、[修复后](evidence/maintenance-finalization/metrics-isolation-after.json) |
| 实际启动、迁移与停止 | 两套增强 API 由新包启动；MySQL 追加迁移已执行，summary 外键 2、新触发器 2；临时 28090 已停止 | [运行结果](evidence/maintenance-finalization/runtime-final.json)、[停止验证](evidence/maintenance-finalization/startup-stop-results.json) |

两轮 2,175 是同一套测试在不同数据库下执行，不是 4,350 个独立新增测试，也不是全部由本轮编写。本轮新增策略持久化、监控 HTTP 隔离及过滤器注册检查；原有单元测试和事务/消息/权限回归一并保留。

Surefire 嵌套 JUnit XML 汇总少计 100 个父类用例；统计工具同时保存 XML 诊断数量，并以 Maven 最终结果与逐测试类控制台数量交叉一致为通过条件。必须有本轮开始时间、完整日志和 BUILD SUCCESS；没有通过降低门槛或忽略跳过来凑通过数。

## 3. 页面测试范围

真实 Web 站点的仪表详情、新建及编辑弹窗经过：默认策略与提交、ONCE/60 编辑回显、关闭重开、非法等待阻止网络提交、403 保留数据、失败后恢复保存、桌面 1440×1000 / 手机 390×844、页面非空及无运行时异常检查。故意注入的 403 是唯一预期控制台错误。

Browser 插件在本次环境不可用，使用现有 Playwright 与系统 Chrome。策略接口的浏览器写操作使用合成夹具，不能冒称真实商业授权下的完整创建链路；数据库持久化、兼容更新、失败回滚由真实 JPA/Liquibase 双数据库集成测试验证。现有许可证拒绝用例仍保留，没有绕过付费边界。截图保存在本地验收目录，未包含真实用户资料；旧页面部分标题仍使用英文回退，不宣称完成全站汉化。

`PER_EPISODE` 以恢复正常读数结束异常周期；完成工单不代表设备恢复。`ONCE` 的历史开单次数不会因恢复或编辑规则清零。等待时间到达后仍需一条被系统接受的异常读数触发，仪表原有读数频率仍有效；这不是高频采集，也不是到时独立定时开单。

## 4. 运行及数据隔离

| 环境 | 前端 | API | 数据库与处理 |
|---|---|---|---|
| 原部署 | http://127.0.0.1:3089 | 18089 | 原配置与原数据保持，整个收尾过程未停止原 API |
| PostgreSQL 增强 | http://127.0.0.1:3289 | 28089 | 本机 25432 的隔离实验库；重启为验证后的新包 |
| MySQL 增强 | http://127.0.0.1:3389 | 28189 | 本机 23306 的 cmms_mysql_lab；仅追加已测试迁移，不清空数据 |
| 临时启停/监控验证 | 无 | 28090 | 不启动外发或后台事件 worker，验收后已停止 |

完整回归使用新的随机测试 schema/库，只清理各自生成且名称符合白名单的测试 schema/库。没有把原 PostgreSQL 数据迁到 MySQL；没有双写。邮件、Push、Webhook 外发保持禁用。

低内存主机在并行构建时曾发生 native memory 分配失败；失败日志保留，没有记为通过。最终将两个增强 API 临时停止，使用 Java 最大堆/处理器限制、前端构建并行数 1，顺序完成全量回归和构建后恢复服务。原部署与数据库未因此停止。

本次运行包：`app-maintenance-20261114T010440013Z-8039bde9.jar`，SHA-256：`fe0ecdfb33e995fb4c4f5bebd787ded7466c827f500fd541674f700c5b75e459`。`api/target/app.jar` 是该包的兼容副本；启动脚本使用唯一命名包，避免下次打包再次覆盖运行中的文件。仅在不存在进程使用旧薄包时才恢复兼容副本；旧包已保存在仓库外。

## 5. 复验步骤与 CI 边界

1. 准备隔离服务：PostgreSQL 25432、MySQL 23306、Redis 26379、RabbitMQ 25672。不要把测试配置指向原部署或共享生产数据库。MySQL 测试账号仅用于测试前缀库；运行账号保持 DML 权限。
2. 设置 Java 17，顺序运行 `scripts/test-maintenance.ps1 -Maven <mvn> -Settings <settings>` 与 `scripts/test-mysql-maintenance.ps1 -Maven <mvn> -Settings <settings> -CredentialFile <私有测试账号文件>`；保存开始时间与原始日志到仓库外。
3. 在每轮报告仍在 `api/target/surefire-reports` 时执行 `python scripts/summarize-maintenance-tests.py --since <开始时间> --log <该轮日志> --min-tests 2100 --require-no-skips --output <脱敏摘要>`。不要使用另一轮日志或直接提交 Surefire XML。
4. 前端目录运行 `node ../scripts/test-maintenance-client.cjs`、`node ../scripts/test-session-socket.cjs`、`node ../scripts/test-meter-policy.cjs`、`npx tsc --noEmit --incremental false`、`npm run build`。小内存主机设置 `CMMS_BUILD_WORKERS=1`、`NODE_OPTIONS=--max-old-space-size=2048`，不与后端全量回归并行；构建时不配置外部 Sentry 上传凭据。
5. 运行 `scripts/test-maintenance-artifact.ps1`、`scripts/test-maintenance-environment.ps1`、`python scripts/test-maintenance-summary.py`；回归通过后运行 `scripts/build-maintenance-artifact.ps1 -Maven <mvn> -Settings <settings> -SkipTests`。此处只跳过重复打包测试，不替代完整回归。
6. `scripts/start-maintenance-lab.ps1 -Java <java> -EnableScheduling` 和 `scripts/start-mysql-maintenance.ps1 -Java <java> -EnableScheduling` 使用验证产物；端口已占用会拒绝启动。停止指定增强实例用 `scripts/stop-maintenance-lab.ps1 -Port <28089或28189>`，不能以 kill 全部 Java 的方式操作。
7. 凭据、JWT 密钥、`.env`、运行数据、数据库、诊断 dump、构建包继续忽略；Git 中只放源码及脱敏证据。当前服务只绑定本机。

工作流已增加 PostgreSQL/MySQL 矩阵和前端门禁，YAML 与 PowerShell 语法通过本地检查；前一轮未推送，也未执行容器；现已在个人 Fork 的功能分支实际跑通 Linux 双库 CI 和容器链路，见 [补充验收](maintenance-remote-verification.md)。本机 Windows 仍未安装 Docker。

## 6. 已知边界和后续工作

- **必须保留的数据库约束**：MySQL 启动器已将实验缓存命名空间从 v1 改为 epoch-v2，保留旧缓存自然过期，不清空共享 Redis。MySQL 独立 epoch 历史表不能随设备清理，否则重建可能再次复用版本。它保留历史计数，允许版本有间隔；不要求连续。原 summary 外键未移除。计数器不能恢复迁移前已删除设备的历史版本；首次升级应轮换缓存命名空间，或确保旧缓存与旧写入者完全过期后再开放写入。本次增强 API 停机回归时间已超过旧缓存 60 秒最大 TTL，且没有旧测试写入实例继续运行。计数器长期增长的容量与归档成本尚未测量。
- **MySQL TRUNCATE**：不触发行级失效。测试明确验证这种差异，随后使用新缓存命名空间。运行账号禁止 DDL/TRUNCATE；恢复备份或重用数据时切换命名空间，不能将其称为与 PostgreSQL TRUNCATE 等价。
- **性能数字**：以前的 PostgreSQL/Redis/MySQL 合成数据测量仍保留在各阶段报告，本轮不重报成新的性能成果。新计数器的写入开销、长期容量及跨主机高可用未测试。
- **外部系统**：后续已补 Mailpit 真实 SMTP 协议、受控 Webhook HTTP 验证；真实邮箱到达、手机 Push、公网 Webhook、生产监控和跨主机 RabbitMQ 高可用仍未验收。原部署外发设置未改变。
- **功能边界**：手机尺寸 Web 页面已验收，独立移动 App 的策略控件未补；工作日历 SLA、更完整规则引擎、遗留通知全部迁移、命令/历史计数保留治理仍属后续范围。
- **贡献边界**：设备、工单、用户/权限、产品基础属于原 Atlas CMMS。仪表周期、库存事务、Redis 查询、RabbitMQ/Outbox、MyBatis-Plus 查询与补投审计等增强来自此前二次开发，本轮继续补齐页面、双库回归、缓存版本修复和启动/监控可靠性；属于 Codex 辅助完成。只有自己复现、理解与能解释的工作才可写成个人掌握，不能把原作者代码或这些测试数量整体包装成原创。

## 7. 统一入口与证据保留

当前验收以本报告和 [验收清单](maintenance-acceptance-checklist.md) 为准。历史审计不删除，历史测试数不累计成当前通过数。公开证据为 [源码哈希](evidence/maintenance-finalization/source-hashes.json)、[日志哈希](evidence/maintenance-finalization/private-log-hashes.json) 及本页链接的 JSON；原日志、截图、旧薄 JAR 备份存于本地 `C:\Users\roxanne\Documents\Codex\work\cmms-finalization`，不提交运行凭据。
