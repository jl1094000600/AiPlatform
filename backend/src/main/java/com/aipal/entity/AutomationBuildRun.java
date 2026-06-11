package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("automation_build_run")
public class AutomationBuildRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pipelineId;
    private Long stageRunId;
    private Long generatedCodeBatchId;
    private String status;
    private String commandText;
    private String workDir;
    private Integer exitCode;
    private String commandLog;
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationMs;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
