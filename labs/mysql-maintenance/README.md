# 独立 MySQL 维修数据实验

这是合成设备维修表的学习实验，不是 CMMS 主库，也没有生产双写。

1. 在本目录执行 `docker compose up -d`，仅占用本机23306端口，使用独立命名卷。
2. 打开两个 `docker compose exec mysql mysql -ulab -plab_only_password maintenance_lab` 会话。
3. 按 `experiments.sql` 的A/B顺序手动执行，记录受影响行数、阻塞、提交/回滚、RC/RR快照和EXPLAIN。
4. 用同样业务不变量与主项目PostgreSQL测试比较：非负库存、条件更新、锁等待和稳定分页。

`schema.sql`仅空卷初始化执行；不用全库迁移或业务双写来学习MySQL。不自动删除卷。当前脚本交付不等于MySQL实验已经在本机执行，结果需另存时间、版本、命令和原始输出。


## 本轮实测补记

2026-11-14 已在原生独立 MySQL 8.0.33 / 23306 / cmms_mysql_learning 上完成6项实验，见 [证据](../../docs/evidence/maintenance-completion/mysql-experiment-results.json)。这是单独的数据目录，未操作现有MySQL80实例；上面的Compose路径仍未实跑。原生实验与Compose不能同时占用23306，请先核对进程身份。
