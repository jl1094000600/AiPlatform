package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("automation_code_requirement_feedback")
public class AutomationCodeRequirementFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long pipelineId;
    private Long stageRunId;
    private Long generationJobId;
    private String feedbackSource;
    private String alignmentStatus;
    private Integer alignmentScore;
    private String summary;
    private String failureReason;
    private String detailJson;
    private String rawResult;
    private String reviewedBy;
    private LocalDateTime createTime;
}
