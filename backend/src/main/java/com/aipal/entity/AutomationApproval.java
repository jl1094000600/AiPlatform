package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("automation_approval")
public class AutomationApproval {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pipelineId;
    private Long stageRunId;
    private String approvalType;
    private String reviewerRole;
    private String status;
    private String comment;
    private String reviewedBy;
    private LocalDateTime reviewedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
