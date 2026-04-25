package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_agent_heartbeat")
public class AgentHeartbeat {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long agentId;
    private String agentCode;
    private String instanceId;
    private LocalDateTime lastHeartbeat;
    private Integer healthScore;
    private String endpoint;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
