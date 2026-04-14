package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("mon_call_record")
public class MonCallRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    private Long agentId;
    private String agentVersion;
    private Long bizModuleId;
    private Long modelId;
    private LocalDateTime requestTime;
    private LocalDateTime responseTime;
    private Integer durationMs;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private Integer statusCode;
    private Integer success;
    private String errorMessage;
    private String requestParams;
    private String responseResult;
    private LocalDateTime createTime;
}
