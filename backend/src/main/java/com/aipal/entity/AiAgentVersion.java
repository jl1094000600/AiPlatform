package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_agent_version")
public class AiAgentVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long agentId;
    private String version;
    private String changelog;
    private String config;
    private Integer status;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
