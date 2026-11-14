# 维修 SLA 与重复报修关联

> 状态入口：本文保留该阶段的源码证据和验收记录；当前实现、测试及启动方式以 [最终收尾验收](maintenance-final-acceptance.md) 为准。历史轮次的测试数、分支和运行地址不能直接当作当前状态。


本文件记录 `cmms-fork` 中的新增实现。原 `cmms` 部署、数据库和配置不迁移。测试运行结果由最终验收清单记录；下面列出实现语义与测试入口，不能仅根据代码存在就宣称测试通过。

## 1. 重复报修关联已有维修工单

- `Request.workOrder` 从一对一改为多对一，追加 Liquibase 迁移仅移除 `request.work_order_id` 的单列唯一约束，保留外键和原值。`WorkOrder.parentRequest` 继续保存最初创建工单的报修来源，不因重复报修被覆盖。
- 新控制器 `RequestLinkController`，命令 `RequestLinkCommandService.link`：重新加载当前用户及角色；只允许本企业具备报修、工单全量查看和维护管理权限的账号；按请求→工单顺序锁定并刷新实体。
- 仅待处理、未取消报修可关联未归档、未完成且设备相同的工单。两边未指定设备也视为相同；一边指定另一边缺失不允许。相同目标再次提交返回已关联结果，不重复写审计或事件；另一目标返回 409。
- 原 `approve/cancel/patch/deleteByIdAndUser` 也锁定同一请求记录，显式校验公司，防止关联与审批/取消/修改并发形成非法状态。已取消报修禁止重新审批，需走明确的新报修流程。同一取消理由（去除首尾空格）的重试直接返回原状态，不再重复工作流/通知；不同理由返回 409，不能覆盖既有取消审计。
- 关联、`request_link_audit` 与 `REQUEST_LINKED` Outbox 同事务。关联成功的注册报修人收到站内通知；工单后续状态更新沿可靠消息模块通知全部关联报修人。没有把多个报修删除或合并为一条记录。
- 关联报修创建人沿原“报修人可看工单”语义获得工单查看权，不获得编辑权。服务器仍须先确认企业范围，不能只靠前端过滤。
- 操作审计以追加记录保留公司、请求、工单、操作者、理由和关联时间。删除关联工单后，沿用原外键 SET NULL 将报修恢复待处理，可显式关联新工单，旧审计不丢失。新 UI 提供待处理报修分页、同设备候选工单分页、确认及原因；错误/409 不自动重放写请求。

接口（均需要认证及服务器新鲜权限检查）：

| 方法 | 路径 | 行为 |
|---|---|---|
| GET | `/request-links/pending?page=0&size=25` | 当前企业待处理报修 |
| GET | `/request-links/{requestId}/candidates` | 同设备可关联工单 |
| POST | `/request-links/{requestId}` | `{workOrderId, reason}`；原因 5–500 字符 |
| GET | `/request-links/{requestId}/audit` | 本企业关联审计 |

## 2. SLA 响应与修复期限

- 独立 `maintenance_sla_policy/case/escalation` 表，保留原工单 `dueDate` 与工时字段，不覆盖原有到期规则。
- 按公司和优先级设置响应、修复分钟数，范围 1–525600，修复期限不短于响应期限。默认不预置策略，也不自动纳管历史数据。
- PostgreSQL 触发器 `cmms_capture_work_order_sla` 与新工单插入同事务快照策略、创建时间、响应/修复截止时间。只纳管策略生效后新建的非演示、非归档、未完成工单。
- 工单修改同事务捕获原系统 `first_time_to_react` 与首次有效完成时间。响应沿用原系统“首次维护操作”口径，不冒称首次接单时间；UTC 无时区历史列显式转为 `timestamptz`。
- 使用自然时间；暂停不停止计时。优先级/策略后续变化不追改已保存期限，停用策略只阻止后续新工单自动纳管，既存周期继续履约。首次完成后该 SLA 周期封存，重新打开工单不重置原周期；后续如需要多维修周期，须另设计周期模型，不能偷偷抹去已发生的超时。
- 管理员可显式纳管已有未完成工单，必须说明理由；截止时间从原创建时间计算，因此可能立即超时。重复纳管不重复创建周期。
- `MaintenanceSlaJob` 每 60 秒扫描；单实例使用 Quartz `@DisallowConcurrentExecution`，跨实例通过数据库工单→case行锁、`SKIP LOCKED`、`(work_order_id,kind)` 唯一约束和稳定 eventId 控制重复。
- 每个周期最多一次响应超时、一次修复超时升级。升级记录、case事件标记、Outbox 同事务；失败后回滚，后续扫描恢复。通知送给分派人员及维护管理员，由消费时当前权限再次过滤。
- “升级”指升级通知给维护管理员，不自动篡改工单优先级或指派人员。页面的“升级已入账”不等于外部邮件已送达。当前 SLA 通知使用可靠站内渠道，没有主动发送真实邮箱。
- 截止时间相等视为按时，严格晚于截止时间才超时。及时完成的工单在后续扫描中不会因当前时钟推进而误判超时。

接口：

| 方法 | 路径 | 行为 |
|---|---|---|
| GET | `/maintenance-sla/policies` | 当前企业策略 |
| PATCH | `/maintenance-sla/policies/{NONE/LOW/MEDIUM/HIGH}` | `{responseMinutes,repairMinutes,enabled}` |
| GET | `/maintenance-sla/cases?page=0&size=25` | 企业 SLA 台账与同范围计数 |
| POST | `/maintenance-sla/cases/{workOrderId}/enroll` | `{reason}` 显式纳管历史工单 |

配置：`cmms.sla.enabled=true` 启用管理 API 和 Quartz job；`cmms.events.enabled=true` 才处理超时投递。关闭 SLA 配置停止管理 API/扫描，不删除已有策略和快照；数据库仍捕获已纳管工单状态，避免暂停期间遗漏首次完成。仅在独立实验 profile 开启，不修改原部署。迁移顺序：先原领域/Outbox迁移，再 `2026_11_14_request_link.xml`、`2026_11_14_maintenance_sla.xml`。

## 3. 前端入口与代码

- `frontend/src/content/own/RequestLink/index.tsx`：重复报修关联，建议路由 `/app/maintenance/request-links`。
- `frontend/src/content/own/MaintenanceSla/index.tsx`：策略、显式纳管、SLA 台账与计数，建议路由 `/app/maintenance/sla`。
- 查询使用当前会话 API helper、取消旧请求、公司/用户/角色变化后重新挂载。写操作按钮锁定并二次确认；前端权限仅为提示，服务端是最终权威。

## 4. 验收测试入口

`MaintenanceSlaRequestIntegrationTest` 使用完整 Spring/Hibernate、Liquibase master 和独立 PostgreSQL schema；外发适配器替换，Quartz不自动启动，测试主动调用真实事务服务。覆盖：

1. 多报修指向同一工单、相同目标重放、审计和Outbox计数。
2. 并发关联不同工单、关联与取消竞争，只能一个合法结果。
3. 已取消/已完成/设备不同/跨企业拒绝。
4. 关联与Outbox外层事务回滚，权限撤销后重放拒绝。
5. 策略不自动纳管历史，新增工单期限快照不随策略/优先级变化。
6. 触发器捕获首次响应/完成，重开不抹掉原周期。
7. 两个SLA工作线程只产生每类一条升级；重复运行不再发。
8. 升级和Outbox原子回滚，及时完成不误报。

`MaintenanceSlaRulesTest` 验证精确截止边界、缺失响应、及时/迟到响应四个时间判断。

上述功能属于此次二次开发；原设备/工单/报修模型、权限框架、工单原始响应时间字段和Quartz依赖来自原项目，不能包装为个人从零实现。
