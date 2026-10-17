package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_assignment")
public class ProjectAssignment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String projectKey;
    private String targetType;
    private Long targetId;
    private Long assigneeUserId;
    private String status;
    private Integer version;
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
