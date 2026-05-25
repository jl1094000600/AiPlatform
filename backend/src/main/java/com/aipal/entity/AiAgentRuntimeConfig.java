package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_agent_runtime_config")
public class AiAgentRuntimeConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long agentId;
    private String agentCode;
    private Long modelId;
    private Long datasetId;
    private Integer topK;
    private Double temperature;
    private String inputField;
    private String expectedField;
    private Long promptId;
    private Long promptVersionId;
    private String systemPrompt;
    private String userPromptTemplate;
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;

    @TableField(exist = false)
    private String modelCode;
}
