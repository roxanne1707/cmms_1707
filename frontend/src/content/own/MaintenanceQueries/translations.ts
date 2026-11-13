export const maintenanceQueryEn: Record<string, string> = {
  maintenance_overview_title: 'Asset work-order overview',
  maintenance_overview_description:
    'Review work-order completion and cycle time for one asset in the current company.',
  maintenance_ledger_title: 'Maintenance ledger',
  maintenance_ledger_description:
    'Track maintenance records by asset, status, priority and meter-triggered source.',
  maintenance_asset: 'Asset',
  maintenance_select_asset: 'Select an asset you can view.',
  maintenance_all_assets_hint:
    'Leave empty to include all matching work orders, including those without an asset.',
  maintenance_asset_search_limit:
    'Showing the first 50 assets; type a name to narrow the search.',
  maintenance_no_assets: 'No matching assets',
  maintenance_clear_asset_search: 'Clear asset search',
  maintenance_asset_pending:
    'Choose an asset from the search results, or clear the search before applying filters.',
  maintenance_start: 'Created from (UTC, inclusive)',
  maintenance_end: 'Created until (UTC, exclusive)',
  maintenance_interval_hint:
    'Filter by work-order creation time in UTC: [start, end), at most 366 days.',
  maintenance_include_archived: 'Include archived work orders',
  maintenance_include_demo: 'Include demo work orders',
  maintenance_apply: 'Apply filters',
  maintenance_refresh: 'Refresh results',
  maintenance_unapplied:
    'Filters have changed. Apply them to view updated results.',
  maintenance_loading: 'Loading maintenance data…',
  maintenance_invalid_date: 'Enter valid start and end times in UTC.',
  maintenance_date_bounds: 'Dates must be within 1900–2200.',
  maintenance_invalid_range:
    'End must be after start, with a maximum interval of 366 days.',
  maintenance_bad_request:
    'Invalid query parameters. Check the dates and filters.',
  maintenance_sign_in: 'Your session has expired. Sign in again and retry.',
  maintenance_access_denied:
    'Your permissions or subscription do not allow this view. Analytics, asset, work-order and other-work-order access are required; the ledger also requires access to other assets.',
  maintenance_not_found:
    'The asset is unavailable, or the maintenance query module is not enabled.',
  maintenance_overloaded:
    'The query service is busy. Refresh and try again shortly.',
  maintenance_load_failed:
    'Could not load maintenance data. Check your connection and retry.',
  maintenance_metric_hint:
    'Completion rate uses the current status of work orders created in this interval. Average cycle time is completion minus creation for valid completed records; it is not MTTR and does not subtract pauses.',
  maintenance_overview_choose:
    'Select an asset and apply filters to view its overview.',
  maintenance_empty_overview:
    'No work orders match this asset and creation-time interval.',
  maintenance_total_orders: 'Total work orders',
  maintenance_completed_orders: 'Currently complete',
  maintenance_completion_rate: 'Completion rate',
  maintenance_cycle_hours: 'Work-order cycle (hours)',
  maintenance_samples:
    'Valid cycle samples: {{valid}}; invalid completion-time samples: {{invalid}}.',
  maintenance_applied_period:
    'Applied interval: {{start}} to {{end}} (end exclusive)',
  maintenance_generated: 'Statistics generated at {{time}}',
  maintenance_status: 'Status',
  maintenance_priority: 'Priority',
  maintenance_source: 'Work-order source',
  maintenance_sort: 'Creation-time order',
  maintenance_all: 'All',
  maintenance_status_OPEN: 'Open',
  maintenance_status_IN_PROGRESS: 'In progress',
  maintenance_status_ON_HOLD: 'On hold',
  maintenance_status_COMPLETE: 'Complete',
  maintenance_priority_NONE: 'None',
  maintenance_priority_LOW: 'Low',
  maintenance_priority_MEDIUM: 'Medium',
  maintenance_priority_HIGH: 'High',
  maintenance_meter_source: 'Meter episode',
  maintenance_other_source: 'Other / not recorded',
  maintenance_newest: 'Newest first',
  maintenance_oldest: 'Oldest first',
  maintenance_ledger_metric_hint:
    'This ledger filters by creation time. Cycle time is shown only for valid completed records. “Other / not recorded” may include historical work orders without source backfill.',
  maintenance_results: 'Matching work orders: {{count}}',
  maintenance_table_utc:
    'All table times use UTC. Open a work-order or asset link to view details.',
  maintenance_empty_ledger: 'No maintenance records match these filters.',
  maintenance_empty_page:
    'This page no longer contains records. Go back a page or refresh the filters.',
  maintenance_work_order: 'Work order',
  maintenance_created: 'Created at',
  maintenance_completed: 'Completed at',
  maintenance_archived: 'Archived',
  maintenance_demo: 'Demo',
  maintenance_no_asset: 'No linked asset',
  maintenance_rows_per_page: 'Rows per page',
  maintenance_page_range: '{{from}}–{{to}} of {{count}}',
  maintenance_next_page: 'Next page',
  maintenance_previous_page: 'Previous page',
  maintenance_page_limit:
    'Maximum page depth reached. Narrow the date or asset filter.'
};

export const maintenanceQueryZh: Record<string, string> = {
  maintenance_overview_title: '设备工单概览',
  maintenance_overview_description:
    '查看当前企业单台设备的工单完成情况与维修周期。',
  maintenance_ledger_title: '维修台账',
  maintenance_ledger_description:
    '按设备、工单状态、优先级和异常来源追踪维修记录。',
  maintenance_asset: '设备',
  maintenance_select_asset: '请选择一台可查看的设备。',
  maintenance_all_assets_hint:
    '不选择设备时包含全部匹配工单及未关联设备的工单。',
  maintenance_asset_search_limit: '显示前 50 台设备；输入名称缩小搜索范围。',
  maintenance_no_assets: '没有匹配设备',
  maintenance_clear_asset_search: '清空设备搜索',
  maintenance_asset_pending:
    '设备搜索尚未确认，请从下拉结果选择设备，或清空搜索后再应用筛选。',
  maintenance_start: '创建时间起点（UTC，包含）',
  maintenance_end: '创建时间终点（UTC，不包含）',
  maintenance_interval_hint:
    '按工单创建时间筛选，UTC 区间为 [起点, 终点)，最长 366 天。',
  maintenance_include_archived: '包含已归档工单',
  maintenance_include_demo: '包含演示工单',
  maintenance_apply: '应用筛选',
  maintenance_refresh: '刷新结果',
  maintenance_unapplied: '筛选条件尚未应用，请点击“应用筛选”查看新结果。',
  maintenance_loading: '正在加载维修数据…',
  maintenance_invalid_date: '请输入有效的 UTC 起止时间。',
  maintenance_date_bounds: '日期须在 1900 至 2200 年内。',
  maintenance_invalid_range: '终点必须晚于起点，且区间不能超过 366 天。',
  maintenance_bad_request: '查询参数无效，请检查日期和筛选条件。',
  maintenance_sign_in: '登录已失效，请重新登录后重试。',
  maintenance_access_denied:
    '当前账号权限或订阅不允许查看此数据。查询需要分析、设备、工单查看及查看他人工单权限；台账还需要查看他人设备权限。',
  maintenance_not_found: '设备不存在、不可访问，或服务尚未启用维修查询模块。',
  maintenance_overloaded: '查询服务暂时繁忙，请稍后点击刷新重试。',
  maintenance_load_failed: '维修数据加载失败。请检查连接并重试。',
  maintenance_metric_hint:
    '完成率按所选创建时间内工单的当前状态计算。平均周期为有效已完成工单的“完成时间−创建时间”，不是 MTTR，不扣除暂停时间。',
  maintenance_overview_choose: '选择设备并应用筛选，即可查看概览。',
  maintenance_empty_overview: '该设备在所选创建时间区间内没有匹配工单。',
  maintenance_total_orders: '工单总数',
  maintenance_completed_orders: '当前已完成',
  maintenance_completion_rate: '完成率',
  maintenance_cycle_hours: '工单周期（小时）',
  maintenance_samples:
    '有效周期样本 {{valid}} 条；无效完成时间样本 {{invalid}} 条。',
  maintenance_applied_period: '已应用区间：{{start}} 至 {{end}}（不包含终点）',
  maintenance_generated: '统计生成时间：{{time}}',
  maintenance_status: '状态',
  maintenance_priority: '优先级',
  maintenance_source: '工单来源',
  maintenance_sort: '创建时间排序',
  maintenance_all: '全部',
  maintenance_status_OPEN: '待处理',
  maintenance_status_IN_PROGRESS: '处理中',
  maintenance_status_ON_HOLD: '已暂停',
  maintenance_status_COMPLETE: '已完成',
  maintenance_priority_NONE: '无',
  maintenance_priority_LOW: '低',
  maintenance_priority_MEDIUM: '中',
  maintenance_priority_HIGH: '高',
  maintenance_meter_source: '仪表异常周期',
  maintenance_other_source: '其他 / 未记录',
  maintenance_newest: '最新优先',
  maintenance_oldest: '最早优先',
  maintenance_ledger_metric_hint:
    '本台账按创建时间筛选。周期仅对有效完成记录计算；“其他 / 未记录”也可能包含未回填来源的历史工单。',
  maintenance_results: '匹配工单：{{count}} 条',
  maintenance_table_utc: '表格时间统一使用 UTC；点击工单或设备名称可查看详情。',
  maintenance_empty_ledger: '没有符合筛选条件的维修记录。',
  maintenance_empty_page: '此页已无记录，请返回上一页或刷新筛选。',
  maintenance_work_order: '维修工单',
  maintenance_created: '创建时间',
  maintenance_completed: '完成时间',
  maintenance_archived: '已归档',
  maintenance_demo: '演示',
  maintenance_no_asset: '未关联设备',
  maintenance_rows_per_page: '每页条数',
  maintenance_page_range: '{{from}}–{{to}} / {{count}} 条',
  maintenance_next_page: '下一页',
  maintenance_previous_page: '上一页',
  maintenance_page_limit: '已达到最大分页范围，请缩小日期或设备筛选。'
};
