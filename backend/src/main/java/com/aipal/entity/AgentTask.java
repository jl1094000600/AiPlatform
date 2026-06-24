package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_task")
public class AgentTask {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId; private Long runId; private Long parentTaskId; private String taskType; private String status;
    private String payloadJson; private Integer attemptCount; private Integer maxAttempts; private String leaseOwner;
    private LocalDateTime leaseUntil; private LocalDateTime availableAt; private LocalDateTime startTime; private LocalDateTime endTime;
    private String errorMessage; private LocalDateTime createTime; private LocalDateTime updateTime; @TableLogic private Integer isDeleted;
}
