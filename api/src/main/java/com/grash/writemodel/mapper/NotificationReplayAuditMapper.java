package com.grash.writemodel.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.grash.writemodel.NotificationReplayAudit;
/** Only the replay command service may append rows, in the same transaction as its recovery action. */
public interface NotificationReplayAuditMapper extends BaseMapper<NotificationReplayAudit> {}
