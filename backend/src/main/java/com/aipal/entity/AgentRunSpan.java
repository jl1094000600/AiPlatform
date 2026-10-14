package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * One observability span inside an agent run timeline. Summaries must stay content-free:
 * no prompt bodies, no memory bodies, no raw tool payloads.
 */
@Data
@TableName("agent_run_span")
public class AgentRunSpan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long runId;
    private Long parentSpanId;
    private String spanId;
    private String traceId;
    private String spanType;
    private String spanName;
    private Long stepId;
    private Long toolCallId;
    private String callId;
    private String status;
    private Integer inputTokens;
    private Integer outputTokens;
    private String summary;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private LocalDateTime createTime;
    @TableLogic
    private Integer isDeleted;
}
