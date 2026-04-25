package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Agent注册事件记录实体类
 * 记录Agent的注册、注销、状态变更等事件
 */
@Data
@TableName("ai_agent_registration_event")
public class AgentRegistrationEvent {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** Agent编码 */
    private String agentCode;

    /** 实例ID */
    private String instanceId;

    /** 事件类型: REGISTER / UNREGISTER / HEARTBEAT_TIMEOUT / STATUS_CHANGE */
    private String eventType;

    /** 变更前状态 */
    private Integer previousStatus;

    /** 变更后状态 */
    private Integer currentStatus;

    /** 事件详情（JSON） */
    private String eventData;

    /** 事件来源: PUSH_API / PULL_PROBE / HEARTBEAT_TIMEOUT */
    private String source;

    private LocalDateTime createTime;
}
