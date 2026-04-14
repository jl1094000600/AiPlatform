package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("biz_agent_auth")
public class BizAgentAuth {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bizModuleId;
    private Long agentId;
    private String agentVersion;
    private Integer qpsLimit;
    private Integer dailyLimit;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
