export const notificationFailureEn = {
  maintenance_failures_title: 'Notification recovery',
  maintenance_failures_description:
    'Review failed maintenance events and external deliveries for your current company.',
  maintenance_failures_scope:
    'Only the latest 100 terminal event failures and 100 failed or skipped deliveries are shown. Counts describe this returned window, not all historical failures. Times are shown in UTC.',
  maintenance_failures_refresh: 'Refresh failures',
  maintenance_failures_events: 'Event processing',
  maintenance_failures_deliveries: 'External delivery',
  maintenance_failures_events_count: 'Failed events in this view',
  maintenance_failures_dead_count: 'Retryable deliveries in this view',
  maintenance_failures_skipped_count: 'Skipped deliveries in this view',
  maintenance_failures_loading: 'Loading notification failures…',
  maintenance_failures_event_empty: 'No terminal event failures in this view.',
  maintenance_failures_delivery_empty:
    'No failed or skipped deliveries in this view.',
  maintenance_failures_event_id: 'Event ID',
  maintenance_failures_delivery_id: 'Delivery ID / Event ID',
  maintenance_failures_handler: 'Handler',
  maintenance_failures_attempt: 'Attempt index / replay generation',
  maintenance_failures_last_error: 'Last failure or skip reason',
  maintenance_failures_updated: 'Updated (UTC)',
  maintenance_failures_created: 'Created (UTC)',
  maintenance_failures_channel: 'Channel / purpose',
  maintenance_failures_state: 'State',
  maintenance_failures_actions: 'Recovery',
  maintenance_failures_replay: 'Replay event',
  maintenance_failures_retry: 'Retry delivery',
  maintenance_failures_dead: 'Failed',
  maintenance_failures_skipped: 'Skipped',
  maintenance_failures_no_reason: 'No reason recorded',
  maintenance_failures_skipped_help:
    'Skipped deliveries cannot be forced to resend. Check current permissions, channel configuration and entitlements first.',
  maintenance_failures_event_help:
    'Replay is allowed only while the latest event attempt is terminally failed and has no successful receipt. A concurrent recovery may change this state.',
  maintenance_failures_confirm_title: 'Confirm notification recovery',
  maintenance_failures_event_confirm:
    'A new processing attempt will be scheduled for this event. The server rechecks its current state and your company administrator permission.',
  maintenance_failures_delivery_confirm:
    'Only this failed delivery will be scheduled again. This does not recreate the station notification or replay the entire event.',
  maintenance_failures_reason: 'Reason for recovery',
  maintenance_failures_reason_hint:
    'Enter 5–500 characters explaining the correction. Do not include passwords, tokens or personal information.',
  maintenance_failures_confirm: 'Confirm and schedule',
  maintenance_failures_pending: 'Scheduling…',
  maintenance_failures_cancel: 'Cancel',
  maintenance_failures_scheduled:
    'Recovery scheduled. This does not mean the notification has been delivered. Refresh later to check for another failure.',
  maintenance_failure_auth_error:
    'Your session has expired. Sign in again before checking failures.',
  maintenance_failure_permission_error:
    'Only an enabled company owner or administrator with an active subscription seat can access notification recovery. The server checks current permissions.',
  maintenance_failure_missing:
    'This record is no longer available in your company. Refresh the list before trying another action.',
  maintenance_failure_unavailable:
    'Notification recovery is unavailable on this server. The reliable events module may not be enabled.',
  maintenance_failure_conflict:
    'The state changed or recovery was already scheduled. Check the latest list before another action; do not repeat the previous request.',
  maintenance_failure_reason_invalid:
    'Enter a reason containing 5–500 characters, excluding leading or trailing whitespace from the minimum length.',
  maintenance_failure_recovery_unknown:
    'The result could not be confirmed. The request may already have been accepted. The list is refreshed before another attempt; a missing row is not proof of delivery.',
  maintenance_failure_load_error:
    'Could not load failures. Check the connection and try Refresh failures.',
  maintenance_failures_rows_per_page: 'Rows per page',
  maintenance_failures_page_range: '{{from}}–{{to}} of {{count}}',
  maintenance_failures_empty_page: '0 records'
};

export const notificationFailureZh: typeof notificationFailureEn = {
  maintenance_failures_title: '通知失败补投',
  maintenance_failures_description:
    '查看当前企业的维修事件处理失败和外部投递记录，并在修正原因后恢复处理。',
  maintenance_failures_scope:
    '仅显示最近 100 条终止失败事件及 100 条失败或跳过的投递。数量只代表本次返回范围，不是全部历史失败总数。时间统一显示为 UTC。',
  maintenance_failures_refresh: '刷新失败记录',
  maintenance_failures_events: '事件处理失败',
  maintenance_failures_deliveries: '外部投递记录',
  maintenance_failures_events_count: '本次失败事件',
  maintenance_failures_dead_count: '本次可补投记录',
  maintenance_failures_skipped_count: '本次跳过记录',
  maintenance_failures_loading: '正在加载通知失败记录…',
  maintenance_failures_event_empty: '本次查询没有终止失败的事件。',
  maintenance_failures_delivery_empty: '本次查询没有失败或跳过的外部投递。',
  maintenance_failures_event_id: '事件编号',
  maintenance_failures_delivery_id: '投递编号 / 事件编号',
  maintenance_failures_handler: '处理器',
  maintenance_failures_attempt: '尝试编号 / 重放代数',
  maintenance_failures_last_error: '最近失败或跳过原因',
  maintenance_failures_updated: '更新时间（UTC）',
  maintenance_failures_created: '创建时间（UTC）',
  maintenance_failures_channel: '通道 / 用途',
  maintenance_failures_state: '状态',
  maintenance_failures_actions: '恢复操作',
  maintenance_failures_replay: '重放事件',
  maintenance_failures_retry: '补投通知',
  maintenance_failures_dead: '失败',
  maintenance_failures_skipped: '已跳过',
  maintenance_failures_no_reason: '未记录原因',
  maintenance_failures_skipped_help:
    '已跳过的投递不能强制重发。请先检查当前权限、通道配置和功能许可。',
  maintenance_failures_event_help:
    '只有最新尝试仍终止失败且没有成功消费回执的事件可重放。其他管理员的操作可能改变其状态。',
  maintenance_failures_confirm_title: '确认通知恢复操作',
  maintenance_failures_event_confirm:
    '将为该事件安排新一轮处理。服务器会再次检查当前状态及你的企业管理员权限。',
  maintenance_failures_delivery_confirm:
    '仅重新安排这一条失败投递，不会重新生成站内通知，也不会重放整个事件。',
  maintenance_failures_reason: '恢复原因',
  maintenance_failures_reason_hint:
    '请输入 5–500 个字符说明已修正的原因，不要填写密码、令牌或个人敏感信息。',
  maintenance_failures_confirm: '确认并安排处理',
  maintenance_failures_pending: '正在安排…',
  maintenance_failures_cancel: '取消',
  maintenance_failures_scheduled:
    '已安排恢复处理，并不代表通知已送达。稍后可刷新查看是否再次失败。',
  maintenance_failure_auth_error: '会话已失效，请重新登录后查看失败记录。',
  maintenance_failure_permission_error:
    '仅已启用且具有有效订阅席位的本企业所有者或管理员可操作。服务器会检查当前权限。',
  maintenance_failure_missing:
    '该记录已不存在或不在当前企业中，请先刷新再操作。',
  maintenance_failure_unavailable:
    '当前服务器未提供通知恢复功能，可能尚未启用可靠事件模块。',
  maintenance_failure_conflict:
    '记录状态已变化或已安排恢复。请先核对最新列表，再决定是否需要操作，不要重复之前的请求。',
  maintenance_failure_reason_invalid:
    '恢复原因必须为 5–500 个字符；最少长度不计首尾空白。',
  maintenance_failure_recovery_unknown:
    '尚未确认请求结果，服务器可能已接收。再次尝试前会刷新列表；记录消失也不代表已经送达。',
  maintenance_failure_load_error:
    '无法加载失败记录，请检查连接后点击“刷新失败记录”。',
  maintenance_failures_rows_per_page: '每页行数',
  maintenance_failures_page_range: '第 {{from}}–{{to}} 条，共 {{count}} 条',
  maintenance_failures_empty_page: '0 条记录'
};
