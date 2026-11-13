# 本地维修可靠性监控

本轮使用 Actuator/Micrometer、Prometheus 3.16.0、Grafana OSS 13.3.1 的原生便携版。它们只服务独立测试环境，不安装 Windows 服务，不写原部署目录。

1. 先启动 `scripts/start-maintenance-lab.ps1 -EnableScheduling`。脚本为28089生成独立的随机指标密码，测试 profile 的 `/actuator/**` 使用单独 HTTP Basic 认证，其他业务接口仍使用自己的 JWT。
2. 将官方校验过的两个工具解压到同一工具目录，包含 `prometheus-3.16.0.windows-amd64/prometheus.exe`、`grafana-13.3.1/bin/grafana.exe`。
3. 执行 `python scripts/start-maintenance-observability.py --tools-root <工具目录>`。端口冲突时脚本拒绝启动，不停止已有进程。
4. 打开本机32909查看target；33090查看Grafana，账号位于忽略文件 `local-runtime/maintenance-observability/access.json`。不要复制账号到文档或提交Git。
5. 看板由本目录JSON自动加载。HTTP延迟单位秒、命中率0–1、堆字节、数据库连接数和消息积压数量分别显示。命中率需要窗口内有实际设备查询；无请求时No data不等于Redis宕机。Redis错误无样本时图中显示0。

进程记录、随机凭据、Grafana SQLite、Prometheus时序数据和日志都在 `local-runtime/maintenance-observability/`。停止时先读取 `processes.json`，核对PID命令行包含这个运行目录和预期程序，再停止对应进程；不要按进程名称批量结束或删除数据库。

本轮官方包校验值：

- Prometheus Windows zip SHA256：`89c03c2f24400fb58668440b6d7226f68564acda27d657e77acfbda42be32cb3`
- Grafana OSS Windows tar.gz SHA256：`cb78dceb990948fe7f1a189a0737fa7f6cf212076fc139a57d48cbc45dc0393f`

不在代码中捆绑这些二进制。未来版本应重新核对官方校验值，而不是沿用本轮值。当前监控验证见 [完成报告](../docs/maintenance-completion-acceptance.md)，不代表已配置生产告警或监控高可用。
