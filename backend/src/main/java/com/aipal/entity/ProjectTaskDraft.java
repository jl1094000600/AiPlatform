package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_task_draft")
public class ProjectTaskDraft {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String projectKey;
    private String sourceType;
    private Long sourceId;
    private String title;
    private String description;
    private Long suggestedAgentId;
    private Long suggestedAssigneeUserId;
    private String status;
    private Long confirmedRunId;
    private String idempotencyKey;
    private Integer version;
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
