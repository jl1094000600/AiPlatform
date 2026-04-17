package com.aipal.agent.image.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_agent_heartbeat")
public class AgentHeartbeat {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long agentId;

    private String instanceId