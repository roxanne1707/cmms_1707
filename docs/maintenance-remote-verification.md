# Atlas CMMS 远程 CI、容器与通知协议补充验收

> 验收日期：2026-11-14。本文补充此前的“尚未运行”项目，不改写历史测试结果。当前最终状态以本页列出的成功提交与运行记录为准。

## 1. 范围和实际结果

本轮在个人 Fork `roxanne1707/cmms_1707` 的 `feat/mysql-maintenance` 分支进行。原 `E:\intern projects\cmms` 目录、数据库、配置及进程未操作；本机两个增强实例也未重启。容器全部运行在 GitHub 托管 Linux Runner。本机没有安装 Docker，不能写成本机 Docker 验收。

| 验证 | 最终结果 | 证据 |
|---|---|---|
| Linux PostgreSQL 全量回归 | **2,175 通过，0 失败/错误/跳过** | [摘要](evidence/maintenance-remote-verification/postgresql-summary.json) |
| Linux MySQL 全量回归 | **2,175 通过，0 失败/错误/跳过** | [摘要](evidence/maintenance-remote-verification/mysql-summary.json) |
| 前端 | 51 项脚本测试、TypeScript 类型检查、生产构建通过 | [完整 CI](https://github.com/roxanne1707/cmms_1707/actions/runs/43556302654) |
| 启动器/CI 门禁 | 两个数据库 job 均通过 9 项产物保护、3 项环境保护、7 项统计门禁及可执行包构建 | [步骤记录](evidence/maintenance-remote-verification/ci-runs.json) |
| Testcontainers | **26 通过，0 跳过**：18 项 PostgreSQL Role 集成测试 + 8 项 SMTP/Webhook 协议测试 | [摘要](evidence/maintenance-remote-verification/testcontainers-summary.json) |
| Compose 应用 | 镜像构建、双库空库迁移、权限、设备/工单/查询与重启持久化全部通过；13 个检查点，临时卷清理成功 | [摘要/镜像 ID](evidence/maintenance-remote-verification/compose-summary.json) |
| 原有服务 | 本机 18089、28089、28189 均 HTTP 200，进程启动时间未变 | [只读记录](evidence/maintenance-remote-verification/local-services-readonly.json) |

成功测试代码提交：`343dfab0de4bcb4f148e7e34318d1314f19b8c14`。完整 CI：[43556302654](https://github.com/roxanne1707/cmms_1707/actions/runs/43556302654)；容器 CI：[43556302641](https://github.com/roxanne1707/cmms_1707/actions/runs/43556302641)。随后提交仅整理文档及脱敏证据，不改变被测代码。

测试不是直接累加的：两套后端执行同一套回归；容器专跑的 18 项 Role 测试与全量回归重叠，新增独立通知协议用例是 8 项。`SENT` 表示 SMTP/HTTP 接受，不表示人已阅读或手机收到。

## 2. 实际发现和修复

### 2.1 远端失败时没有诊断产物

首次 MySQL 失败时，统计脚本在输出 JSON 前退出，导致没有可下载的失败摘要。现在失败构建仍会生成 `FAIL` 摘要，包含数量、测试类/方法、异常类型及源码栈位置；不会上传原始 Surefire XML、数据库连接参数或邮件内容。Maven 汇总与逐类数量仍需匹配，失败、跳过、缺少最终统计或构建未成功都会阻止通过。

源码：`scripts/summarize-maintenance-tests.py`；7 项门禁自测位于 `scripts/test-maintenance-summary.py`。

### 2.2 MySQL 新建容器的触发器迁移权限

Windows 原生实验库已通过不代表 Linux 默认镜像配置相同。新容器的 binlog 与触发器创建权限影响 Liquibase 初始化，造成应用上下文加载失败。CI 现在先用受限测试账号验证默认配置下错误 1419，再仅在该临时实例设置 `log_bin_trust_function_creators=1`，验证受限账号可创建并执行触发器，然后运行完整回归。没有向应用授予 SUPER，没有改动本机 MySQL。

配置位置：`.github/workflows/maintenance-tests.yml`、`compose.maintenance-verification.yml`。这是隔离测试初始化条件，不是可直接复制到共享生产数据库的权限方案；实际部署应由受控迁移账号/管理员完成触发器迁移，业务运行账号仍保持所需 DML 权限。[MySQL 官方说明](https://dev.mysql.com/doc/refman/8.0/en/stored-programs-logging.html)。

### 2.3 Linux MySQL 表名大小写

迁移权限修复后，2,175 项中 2,174 项通过，仅 `MaintenanceJpaIntegrationTest.fullContextAndMasterMigrationAreUsable()` 报 SQL 语法/表定位错误。该测试把 MySQL Liquibase 管理表写作 `databasechangelog`；Linux 默认区分表名大小写，真实表名是 `DATABASECHANGELOG`。现在 MySQL 分支使用实际大写名称，PostgreSQL 分支保留小写，业务断言与测试数量不变。源码：`api/src/test/java/com/grash/integration/MaintenanceJpaIntegrationTest.java:161`。

### 2.4 SMTP 附件字节完整性

首次使用真正 SMTP 协议捕获时，附件断言失败；此前 HTTP 测试接收器并未覆盖 MIME/SMTP 编码。为不可变 PDF 快照显式指定 Base64 传输编码，避免 ASCII 内容按 7bit 传输时发生换行规范化，影响 PDF 偏移或签名。保留严格的内容类型、编码及逐字节断言，未降低测试标准。

源码：`api/src/main/java/com/grash/event/EventDeliverySender.java` 的 `sendSmtp()`（第 138 行，编码保护第 155 行）；回归：`api/src/test/java/com/grash/event/DeliveryProtocolContainerIT.java` 的 `reportAttachmentReachesSmtpWithoutByteChanges()`（第 125 行）。该测试使用合成 PDF 字节验证传输完整性，不代表 PDF 阅读器渲染测试。

### 2.5 容器健康检查及业务身份

实验 profile 的 Actuator 要求独立监控身份，最初的匿名探针无法作为就绪判断。容器验证现生成独立的随机监控密码，使用 HTTP Basic 健康探针；业务使用正常注册的合成企业管理员，不使用引导超级管理员充当企业身份，也不绕过权限或许可。

源码：`scripts/verify-maintenance-containers.py:35`（容器归属校验）、第 47 行（HTTP 请求）和第 60 行（就绪判断）。健康检查通过后才执行 API 验证；注册、登录、设备和工单均在全新容器库内。

本次隔离网络的 Runner 回环探针未通过；改为校验 Compose project/service 标签、读取该项目内部 IP 后直连 8080，并明确禁用 HTTP 代理，才跑通完整链路。这里没有单独鉴别回环映射与代理路径各自的影响，不能据此断言 Docker 存在缺陷。此验收证明应用在隔离容器网络可运行，不证明宿主机回环端口映射或公网入口已验收。

## 3. 容器与协议验证具体覆盖

- 通过原 `api/Dockerfile` 从源码构建可执行镜像，不把本机已运行的 JAR 当作容器构建结果。
- PostgreSQL/MySQL 从空库运行 Liquibase，启动真实 Spring Boot API；Redis 与 RabbitMQ 使用独立容器。
- 拒绝匿名身份查询；合成企业注册、登录及 `/auth/me` 返回正确身份。
- 创建和读取设备、关联维修工单；设备概览与 MyBatis 维修台账能读到已提交工单。
- 重启两套 API 容器，重新登录并验证设备、工单及其关联仍保留。
- Testcontainers 启动真正 PostgreSQL 并执行 Role 集成测试，没有回退成本机 JDBC，也没有 Docker 不可用时自动跳过。
- Mailpit 捕获真实 SMTP AUTH/MIME：中文主题正文、收发地址、PDF 内容及编码；撤权/禁用外发时无捕获邮件，静默 SMTP 服务超时不能误报成功。
- 受控 Webhook HTTP 接收器验证 503 失败传播、307 不跟随跳转、请求超时；已有签名与稳定投递 ID 测试仍在全量回归中。

Testcontainers 测试依赖从 1.19.3 更新到 1.21.4，保留 Java 17/Spring Boot 3；1.21.4 是兼容近期 Docker Engine 的 1.x 修订，[官方发布记录](https://github.com/testcontainers/testcontainers-java/releases/tag/1.21.4)。Mailpit 使用 `axllent/mailpit:v1.33.1`，通过其 [官方 API](https://mailpit.axllent.org/docs/api-v1/)读取捕获邮件；没有配置外部 SMTP relay、没有调用 release 接口。

## 4. 隔离、清理和复验

Compose 项目名为 `cmms-ci-<run-id>-<attempt>`，仅允许 GitHub Linux Runner 执行验证脚本。数据库不发布宿主端口，Compose 中 API 映射限制在 Runner 回环 38089/38189，但实际验收 HTTP 使用该项目内部 IP:8080；默认网络 `internal: true`，没有宿主数据挂载。密码和 JWT 密钥随机生成，临时 env 存在 Runner 临时目录，不上传；清理只操作校验后的本次 Compose 项目及其卷。最终记录 Docker Engine 29.4.2、Compose 5.1.3、linux/amd64 和各镜像 ID；两套 API 初次启动和重启共记录 4 次 Boot 启动成功。

现有原 `docker-compose.yml` 未修改；没有运行全局 Docker prune，也没有迁移/删除用户数据库。两个工作流只在指定功能分支推送或手动触发；没有合并 main、创建发布标签或运行 DockerHub/Koyeb/Netlify 部署工作流。

复验入口：

1. Fork 中选择 **Maintenance enhancement verification**，在功能分支运行：双数据库回归、前端、统计门禁、启动器保护、可执行包。
2. 选择 **Maintenance container verification**：Testcontainers/SMTP 和真实 Compose API 两个 job。
3. 下载摘要 artifact，核对 commit、数量、`result=PASS`、`skipped=0`；Compose 同时要求 `cleanup=true`。
4. 不要把 `DeliveryProtocolContainerIT` 加入无 Docker 的原生回归默认集合；MySQL 脚本默认选择 `*Test,*Tests`，容器 job 显式选择该 IT。

## 5. 仍然不能宣称通过的内容

用户确认没有 SMTP 测试账号、Push 测试设备或公网 Webhook 端点。因此本轮完成真实本地 SMTP 协议与受控 HTTP 验证，但**没有真实邮箱最终到达、外部服务商 TLS/授权、手机 Push 接收或公网 Webhook 投递验收**。没有向现有用户、GitHub 账号邮箱或任意第三方地址发送消息。

跨主机高可用、长时间浸泡、生产监控、原数据库迁移/双写，以及旧报告列出的后续功能，仍保持原边界；本次不把它们计为已验证。当前本机运行实例仍使用上一轮已验收的不可变 JAR，本轮 SMTP 修复已在提交与 CI 新构建镜像验证，未替换正在运行的实例。

## 6. 证据和贡献边界

- [最终工作流步骤与状态](evidence/maintenance-remote-verification/ci-runs.json)、[被测源码 SHA-256](evidence/maintenance-remote-verification/source-hashes.json)。
- [初始 MySQL 迁移失败](evidence/maintenance-remote-verification/before-mysql-initialization.json)、[仅剩大小写错误的 2,174 通过记录](evidence/maintenance-remote-verification/before-mysql-case-fix.json)、[SMTP 附件修复前记录](evidence/maintenance-remote-verification/before-smtp-encoding.json)。
- [公开分支安全扫描](evidence/maintenance-remote-verification/publish-preflight.json)：已检查的代码历史无本机已知密钥匹配、无运行目录/数据库文件路径进入提交。临时凭据和原始日志不发布。
- [统一验收清单](maintenance-acceptance-checklist.md)、[前一轮本地验收](maintenance-final-acceptance.md)。

原 CMMS 设备、工单、用户及权限产品能力属于 Atlas CMMS。本轮新增容器/协议验证、失败门禁诊断和 SMTP 编码修复属于 Codex 辅助完成的增强；测试数量包括原作者测试及此前增强，不能包装成全部独立原创。历史失败和取消的诊断运行保留，只有明确成功的最终运行作为本次验收依据。
