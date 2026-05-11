package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("automation_pipeline")
public class AutomationPipeline {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String pipelineCode;
    private String productLine;
    private String projectName;
    private String requirementTitle;
    private String requirementSummary;
    private String ownerRole;
    private String initiator;
    private Long initiatorUserId;
    private String initiatorUsername;
    private String templateFile;
    private String projectMode;
    private String codeLevel;
    private Integer generateFrontend;
    private Integer generateBackend;
    private String frontendOutputPath;
    private String backendOutputPath;
    private Long skillId;
    private String skillSnapshot;
    private String status;
    private String currentStage;
    private Integer totalStages;
    private Integer passedStages;
    private Integer failedStages;
    private Integer approvalRequired;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
