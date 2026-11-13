# CMMS Git 恢复与迁移验证

日期：2026-11-13。用户已授权：迁移、测试通过后提交并推送独立分支。

## 1. 基线与官方历史

- 官方：<https://github.com/Grashjs/cmms>；个人 Fork：<https://github.com/roxanne1707/cmms_1707>（沿用 GitHub 返回的已有 Fork）。
- 原运行目录：`E:\intern projects\cmms`；新开发目录：`E:\intern projects\cmms-fork`。
- 官方基线：`06abfe35d09372ff80f889fcde73eb3cc71dc205`，`2026-11-05T22:08:52Z`，`fix: increase database connection pool size to 50`。
- GitHub 递归文件树未截断；2,503 个官方 blob 文件与原目录逐一计算 Git 对象哈希：2,502 个逐字节相同，仅根 `.gitignore` 增加了本地运行目录排除规则；无缺失文件、无仅换行差异。
- 完整克隆，无 `--depth`；`git rev-parse --is-shallow-repository` 为 `false`，迁移提交之前 `git rev-list --count --all` 为 **2,433**；`git fsck --full --no-reflogs` 退出码 0、无错误输出。
- `origin` 指向个人 Fork；`upstream` 拉取地址指向官方，其 push 地址设为 `DISABLED`，防止误推官方。分支为 `chore/preserve-local-audit`，不改写 `main` 或历史标签。

## 2. 迁移内容与原目录保护

仅迁移 8 个新增审计文档/证据文件，增强 `.gitignore`，并新增本验证说明。原报告中的 61 个源码链接改为相对链接，补充 Git 基线说明。其余 7 个证据文件复制时与原件逐字节一致；随后仅移除 database-metadata.txt 中 psql 表格行尾的填充空格，数据内容不变，其余 6 个保持逐字节一致；历史测试摘要和 `local_git_sha: null` 保留原审计事实。

未改 `api/`、`frontend/`、`mobile/`、`home/`、CI、依赖清单、Liquibase 或业务代码。未把已发现问题包装为本次已修复：本次贡献是版本基线恢复、审计成果归档和敏感文件隔离。

原目录仍没有 `.git`；迁移后重新核对官方文件及 8 个新增文件，原源码/审计文件没有被迁移操作改写。没有移动或删除原目录、停止服务、导入/导出数据库或切换部署。现有 Maven 缓存只用于复用依赖；本次构建使用离线模式。

`local-runtime/` 的部署脚本、示例数据脚本、低内存构建脚本及说明仍完整保留在原目录，但没有原样公开提交。数据库、存储文件、账户配置、私钥、日志、进程记录、依赖缓存和构建产物均不迁移。原目录保留并不等于另建了备份；本次未声称生成数据库灾备副本。

## 3. 新目录编译与原有测试实测

环境：Windows；Amazon Corretto OpenJDK **17.0.8+7-LTS**；Maven **3.9.4**。使用原本机 Maven settings 指定的已有缓存；settings 本身不纳入 Git。Maven 最大堆 384 MiB，测试 fork 最大堆 512 MiB、fork 数 1；原 POM 的 JUnit 类并行配置保持不变，不宣称已串行化全部测试。

执行目录：新仓库 `api/`。命令结构（本机路径以变量替代，便于复用）：

```powershell
$env:JAVA_HOME = '<JDK17目录>'
$env:MAVEN_OPTS = '-Xmx384m'
$testClasses = 'WorkOrderServiceTest,RequestServiceTest,PartServiceTest,ScheduleServiceTest,RefreshTokenServiceTest,NotificationServiceTest,CacheKeyUtilsTest,CacheConfigTest,TenantAspectTest,WorkOrderCreationJobTest'
mvn -B -o -s '<本机Maven-settings路径>' test "-Dtest=$testClasses" `
  '-DforkCount=1' '-DreuseForks=true' `
  '-DargLine=-Xmx512m -Duser.timezone=UTC --add-opens java.base/java.lang=ALL-UNNAMED'
```

**实测结果：重新编译 773 个生产源文件、93 个测试源文件；474 项测试通过，失败 0、错误 0、跳过 0，BUILD SUCCESS。** Maven 总耗时 `01:00 min`，完成时间 `2026-11-13T22:56:20+08:00`。这是单次本机验证耗时，不是性能基准。

以下逐个计数新目录 Surefire XML 的实际 `testcase` 元素（按 classname/name 校验无重复），并归并嵌套测试类。注意 XML 顶层 `tests` 属性合计为 424，与实际 testcase 数不一致；这里采用与 Maven 控制台汇总一致的 474 个实际 testcase，不依赖该头部属性。该报告格式差异的原因本轮未进一步定位，不推断为业务测试失败。

| 原有测试类 | 测试数 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|
| `CacheConfigTest` | 2 | 0 | 0 | 0 |
| `CacheKeyUtilsTest` | 6 | 0 | 0 | 0 |
| `NotificationServiceTest` | 31 | 0 | 0 | 0 |
| `PartServiceTest` | 62 | 0 | 0 | 0 |
| `RefreshTokenServiceTest` | 9 | 0 | 0 | 0 |
| `RequestServiceTest` | 42 | 0 | 0 | 0 |
| `ScheduleServiceTest` | 55 | 0 | 0 | 0 |
| `TenantAspectTest` | 9 | 0 | 0 | 0 |
| `WorkOrderCreationJobTest` | 6 | 0 | 0 | 0 |
| `WorkOrderServiceTest` | 252 | 0 | 0 | 0 |
| **合计** | **474** | **0** | **0** | **0** |

之后在新仓库执行 `docs/audit-evidence/run-audit-probes.ps1`，退出码 0，复现 4 项既有探针断言：缓存键碰撞、20 小时统计取整、连续异常读数重复创建、库存调用早于权限拒绝。它们是 Mockito/纯 Java 层证据，不证明数据库事务或真实并发问题已完成集成验证。输出仅位于新仓库被忽略的 `local-runtime/audit-probes`。

**未验证范围：**没有运行整个测试套件；本机未发现 Docker，未运行 Testcontainers 数据库集成测试；未启动第二套后端应用，未连接原 `atlas` 数据库验证新部署；未验证性能改善。对这些事项不报告“通过”。

## 4. 提交边界与敏感数据保护

新增排除规则涵盖 `local-runtime/`、`.env` 及实际环境变体、私钥/证书私钥容器、数据库备份和本地数据；保留上游 `.env.example` 模板及原有构建目录排除规则。

提交准备采用明确的 10 文件白名单（8 个原审计文件、`.gitignore`、本说明），不复制整个部署目录，不执行强制暂存。8 个迁移候选的常见秘密模式初检无命中；提交前还复核最终文件列表、暂存差异，并对 16 个模拟敏感/产物路径及 2 个模板路径执行忽略规则检查。模式扫描有局限，不能视为完整安全认证；`.gitignore` 也不能清理已跟踪的历史秘密或阻止使用 `git add -f` 的人为绕过。

上游两个部署 workflow 的 push 入口分别为 `main`/`v*` 标签及 `main` 的移动端路径；此次只推送独立工作分支，不推送标签、不创建 PR、不部署。

## 5. 本机证据与后续使用

比对清单、迁移计划、扫描记录、完整测试/探针日志保存在本机 `Codex/work/cmms-git-recovery/`；不把含本机 classpath 的全部日志上传。以下摘要哈希便于核对原始日志：

- `new-checkout-tests.log`：`4356d9d765d64366431c5e069328a3f32e3cb20265e1bb933df0b2acd4df3cc3`
- `new-checkout-probes.log`：`4ae5258ba7b7eefe1603298a0f9179f9345e0a3269836414df02672606d68af4`

以后在新目录进行源码开发；旧目录暂时只承担现有部署。需要将新代码投入本机部署时，另行准备隔离配置和迁移方案，不把旧库配置直接提交到 Fork。部署辅助脚本若需版本化，应先做参数化及脱敏，再单独提交。
