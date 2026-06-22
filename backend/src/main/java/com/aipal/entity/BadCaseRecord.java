package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bad_case_record")
public class BadCaseRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String caseCode;
    private String sourceType;
    private String stage;
    private String badcaseType;
    private String severity;
    private String projectName;
    private String requirementTitle;
    private String inputPrompt;
    private String generatedPrd;
    private String generatedCode;
    private String expectedBehavior;
    private String failureReason;
    private String reviewedBy;
    private Long pipelineId;
    private Long stageRunId;
    private Long batchId;
    private Long feedbackId;
    private Long approvalId;
    private String tags;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
