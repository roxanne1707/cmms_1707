# 登录会话与权限新鲜度增强

> 状态入口：本文保留该阶段的源码证据和验收记录；当前实现、测试及启动方式以 [最终收尾验收](maintenance-final-acceptance.md) 为准。历史轮次的测试数、分支和运行地址不能直接当作当前状态。


开发目录为 `cmms-fork`；原部署、原数据库不迁移。本页记录实现边界，最终测试结果以统一验收清单和对应日志为准。

## 业务与兼容性

- 维修人员可查看自己的登录会话，退出当前设备、指定设备或全部设备。会话是一次登录，不是硬件指纹；同浏览器再次登录也会产生新会话。
- 新登录、注册、LDAP、SSO、切换账号均使用 `RefreshTokenService.createTokenPair`，JWT 增加 `sid`，刷新令牌保留相同 `session_id`。
- 原 `POST /auth/logout` 保留全设备退出语义，老客户端响应格式不变。Web 原退出按钮使用新“当前设备退出”；旧无 `sid` JWT 先通过既有刷新令牌升级，不静默退出其他设备。
- 旧 JWT 按原到期时间与数据库最新撤销时间验证；旧刷新令牌首次有效轮换建立新会话。迁移前的历史登录无法可靠推断为物理设备，因此不编造设备记录。
- 所有会话接口要求个人 JWT，API key 不能管理交互登录。会话列表仅属于当前用户，最多显示最近 100 条；操作中的用户 ID 永远来自认证身份，不接受请求体指定。

## 具体实现

| 入口 | 文件与行为 |
|---|---|
| 持久化 | `db/changelog/2026_11_14_auth_sessions.xml` 新增 `auth_session`、`refresh_token.session_id` 外键和索引，保留旧令牌列及数据 |
| 会话状态 | `security/SessionRegistry.java` 用 PostgreSQL 维护创建、轮换、到期、撤销时间；不把撤销状态缓存到 Redis/Caffeine |
| 并发控制 | 登录、刷新、退出按 `own_user` 行锁在会话/令牌行之前排序；退出全部与刷新串行，防止撤销后刷新再生成有效会话 |
| 当前设备退出 | `POST /auth/sessions/logout-current`，撤销该 sid 及其刷新令牌；其他 sid 保持有效 |
| 指定设备退出 | `DELETE /auth/sessions/{id}`，查询和更新均包含 user_id，猜到他人 UUID 仍返回 404 |
| 全部退出 | `POST /auth/sessions/logout-all`，同事务撤销会话与刷新令牌，并更新旧 JWT 撤销时间 |
| 列表 | `GET /auth/sessions`，仅返回时间、客户端 User-Agent、状态与当前标志，不返回 token/hash/IP |
| JWT | `security/JwtTokenProvider.java` 每次请求检查 sid 持久状态；新 sid 不依赖 JWT 秒级 issuedAt 与数据库毫秒时间比较，因此刚退出再登录不会误杀新会话 |
| 新鲜权限 | `CustomUserDetailsService` 与默认 `UserService.whoami(request)` 使用新查询而非 Caffeine 用户实体；角色权限在短事务内初始化，减少旧控制器继续使用过期角色图的风险 |
| WebSocket | `WebSocketAuthInterceptor` 要求已认证 CONNECT，只允许自身通知/导入/导出订阅，拒绝客户端直发 broker；入站命令和出站消息重验 JWT，撤销/到期后丢弃后续消息 |
| 页面 | `frontend/src/content/own/SessionManagement/index.tsx`，列表、当前标记、确认退出、旧会话升级、错误反馈与防重复提交 |

刷新令牌新会话族内重用只撤销该族；迁移前没有 session_id 的历史令牌继续采用原先保守的全局重用撤销政策。原始刷新令牌仅客户端保存，数据库仍存 SHA-256 hash。

## 需要能解释的取舍

1. JWT 签名验证只能证明令牌由服务器签发，不能证明尚未退出。即时撤销引入一次持久化状态检查，牺牲完全无状态性。
2. Redis 不作为撤销事实源，否则缓存过期/断连时容易在“误放行”和“全拒绝”之间混淆。本系统规模下优先 PostgreSQL 明确一致性。
3. 不对每个 HTTP 请求更新 lastSeen；页面显示最近令牌刷新时间，避免每次请求写库造成写放大。
4. “即时”指撤销事务提交后的下一次鉴权检查。已开始的事务、已经交给网络发送的消息不能撤回，不宣称绝对零窗口。
5. User-Agent 由客户端提供，可伪造，只作为设备识别提示，不用于认证或风控决策。
6. 旧客户端仍可登录、刷新和全退；升级后的单设备语义只适用于有 sid 的会话。API key 撤销仍走原有 API key 管理，不混同个人登录退出。
7. 该改动使授权状态更新及时可见，不等于全系统每个领域对象的越权条件均已形式化证明。

## 自动化验证

- `SessionRegistryPostgresIntegrationTest`：真实 PostgreSQL 随机临时 schema，仅接受 `127.0.0.1:25432/cmms_enhancement_test`；验证设备隔离、跨用户拒绝、同 sid 轮换、重用检测、全退后立即登录、禁用、回滚、过期、并发全退/刷新及重复撤销。
- 刷新 repository 使用 JDBC 支持的 double，事务与锁是真实 PostgreSQL；不能冒充 Hibernate 全链路测试。
- `AuthSessionControllerTest`：当前身份/sid 绑定、个人 JWT 要求、旧会话返回明确冲突、列表边界。
- `JwtTokenProviderTest`、`RefreshTokenServiceTest`、`RefreshTokenTransactionIntegrationTest`：旧令牌兼容、JWT 拒绝、轮换与回滚规则。
- `WebSocketAuthInterceptorTest`：匿名/跨用户订阅拒绝、自己的导入导出兼容、禁客户端发布、撤销后出站抑制、断开清理。
- `UserServiceTest`、`CustomUserDetailsServiceTest`、`OAuth2AuthenticationSuccessHandlerTest`、`AuthControllerTest`：原登录注册/SSO/角色/退出契约回归。
- 本地 HTTP 验收脚本只在 28089 对合成只读用户进行多登录/撤销；不会调用真实邮箱、公共 Webhook 或原服务。

所有新增代码仍遵守原许可证。测试通过数量、浏览器检查和运行结果需由执行日志填写，不由本设计说明推断。
