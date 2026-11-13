package com.grash.writemodel;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.UUID;
/** Append-only domain audit. No controller exposes generic BaseMapper writes. */
@Data
@TableName(value="event_replay_audit",autoResultMap=true)
public class NotificationReplayAudit {
    @TableId(type=IdType.INPUT) private UUID id;
    private Long companyId;
    private Long actorId;
    private UUID resourceId;
    private String resourceType;
    private String reason;
}
