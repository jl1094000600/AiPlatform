package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_memory_feedback")
public class AiMemoryFeedback {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private Long memoryId;
    private String traceId;
    private String feedbackType;
    private String message;
    private Long createdByUserId;
    private String createdBy;
    private LocalDateTime createTime;
}
