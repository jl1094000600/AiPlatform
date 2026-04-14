package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("mon_api_metrics")
public class MonApiMetrics {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long agentId;
    private Long bizModuleId;
    private Long modelId;
    private LocalDate statDate;
    private Integer statHour;
    private Integer totalCalls;
    private Integer successCalls;
    private Integer failedCalls;
    private Long totalDurationMs;
    private BigDecimal avgDurationMs;
    private Integer p95DurationMs;
    private Integer p99DurationMs;
    private Integer maxDurationMs;
    private Long totalInputTokens;
    private Long totalOutputTokens;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
