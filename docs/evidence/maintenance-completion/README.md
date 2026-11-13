# 本轮验收证据

这些是本机独立测试的去敏摘要和截图，不包含密码、访问令牌、私有配置或数据库备份。完整原始测试日志保留在本机 Codex 工作证据目录。

- completion-summary：完整后端计数、前端结果、源码摘要及后端jar哈希。
- api-performance-results / sql-performance-results：逐轮请求延迟、正确性、SQL执行计划；具体负载及解释见完成报告。
- quartz-cluster-results：两个实际JVM的正常执行及停止节点恢复，数据库侧去重计数。
- sla-request-database / sla-post-scheduler-browser：两条报修关联和两类升级；没有真实外发。
- recovery-database / recovery-browser：管理员恢复审计、消费幂等与安全跳过邮件。
- session-http / session-browser / final-pages-browser：真实会话退出与最终界面导航/手机布局。
- proxy-reset-results：50次本地重置连接后预览服务仍为200。
- observability-results / monitoring-browser：实际抓取、权限和渲染。
- mysql-experiment-results：独立原生MySQL学习实验，未将CMMS迁至MySQL。

截图中的账号、工单和设备均为合成验收数据。不同阶段数据数量可变，不应把旧截图的30张工单当成永久总数。Docker、远端CI及真实外发不在通过证据中。
