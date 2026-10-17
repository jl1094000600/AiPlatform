package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_assignment_event")
public class ProjectAssignmentEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String projectKey;
    private Long assignmentId;
    private String eventType;
    private Long fromAssigneeUserId;
    private Long toAssigneeUserId;
    private String fromStatus;
    private String toStatus;
    private Long actorUserId;
    private String actorName;
    private String reason;
    private LocalDateTime createTime;
}
