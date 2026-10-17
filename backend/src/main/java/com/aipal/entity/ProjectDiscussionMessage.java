package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_discussion_message")
public class ProjectDiscussionMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String projectKey;
    private Long senderUserId;
    private String senderName;
    private String messageType;
    private String content;
    private String idempotencyKey;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
