package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("automation_code_quality_issue")
public class AutomationCodeQualityIssue {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long runId;
    private Long pipelineId;
    private Long stageRunId;
    private String ruleCode;
    private String severity;
    private String category;
    private String filePath;
    private Integer lineStart;
    private Integer lineEnd;
    private String title;
    private String description;
    private String suggestion;
    private LocalDateTime createTime;
}
