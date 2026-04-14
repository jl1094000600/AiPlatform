package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_a2a_task")
public class A2ATask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskId;
    private String sessionId;
    private Long workflowId;
    private Long sourceAgentId;
    private Long targetAgentId;
    private String taskType;
    private String taskDescription;
    private String context;
    private String responseFormat;
    private String status;
    private String result;
    private String errorMessage;
    private Integer timeout;
    private Integer retryCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
}
