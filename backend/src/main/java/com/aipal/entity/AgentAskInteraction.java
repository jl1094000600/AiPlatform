package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Durable ask_user interaction. Survives process restarts so an already-answered or
 * already-timed-out ask is never re-blocked, and duplicate responds are idempotent.
 */
@Data
@TableName("agent_ask_interaction")
public class AgentAskInteraction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long runId;
    private String callId;
    private String question;
    /** PENDING / ANSWERED / TIMED_OUT / CANCELLED */
    private String status;
    private String answer;
    private Integer timeoutSeconds;
    private LocalDateTime deadline;
    private LocalDateTime askedAt;
    private LocalDateTime answeredAt;
    private LocalDateTime resolvedAt;
    private String resolutionReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
