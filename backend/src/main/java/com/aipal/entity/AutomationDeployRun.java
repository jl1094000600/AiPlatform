package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("automation_deploy_run")
public class AutomationDeployRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pipelineId;
    private Long stageRunId;
    private Long deployProfileId;
    private String stageKey;
    private String deployType;
    private String environmentName;
    private String status;
    private String profileSnapshot;
    private String commandLog;
    private Integer exitCode;
    private String imageName;
    private String containerName;
    private Integer jenkinsBuildNumber;
    private String jenkinsBuildUrl;
    private Integer healthStatusCode;
    private Integer healthResponseMs;
    private String healthMessage;
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationMs;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
