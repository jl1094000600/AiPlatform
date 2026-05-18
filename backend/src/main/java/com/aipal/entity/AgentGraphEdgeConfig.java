package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_agent_graph_edge")
public class AgentGraphEdgeConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sourceAgentId;
    private String sourceAgentCode;
    private Long targetAgentId;
    private String targetAgentCode;
    private String edgeType;
    private String triggerIntent;
    private String conditionExpression;
    private String paramMapping;
    private Integer timeoutSeconds;
    private Integer retryCount;
    private Integer enabled;
    private String suitabilityLevel;
    private Integer suitabilityScore;
    private String suitabilityMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
