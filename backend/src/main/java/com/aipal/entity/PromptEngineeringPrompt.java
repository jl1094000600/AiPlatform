package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prompt_engineering_prompt")
public class PromptEngineeringPrompt {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String promptCode;
    private String promptName;
    private String description;
    private Long agentId;
    private String agentCode;
    private String agentName;
    private String projectName;
    private String projectKey;
    private Long pipelineId;
    private Long latestVersionId;
    private Long publishedVersionId;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private Integer latestScore;
}
