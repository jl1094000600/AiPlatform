package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_run_event")
public class AgentRunEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long runId;
    private String fromStatus;
    private String toStatus;
    private Long actorUserId;
    private String actorName;
    private String reason;
    private String traceId;
    private LocalDateTime createTime;
    @TableLogic
    private Integer isDeleted;
}
