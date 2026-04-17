package com.aipal.agent.image.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_agent_config")
public class ImageAgentConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long agentId;

    private String agentCode;

    private String agentName;

    private String description;

    private String category;

    private String apiUrl;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
