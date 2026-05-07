package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_agent")
public class AiAgent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String agentCode;
    private String agentName;
    private String description;
    private String category;
    private String apiUrl;
    private String httpMethod;
    private String requestSchema;
    private String responseSchema;
    private Integer status;
    private Long ownerId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;

    @TableField(exist = false)
    private LocalDateTime lastHeartbeat;

    @TableField(exist = false)
    private Integer instanceCount;

    @TableField(exist = false)
    private String runtimeStatus;
}
