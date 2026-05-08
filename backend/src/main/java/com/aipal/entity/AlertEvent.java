package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("alert_event")
public class AlertEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ruleId;
    private String ruleName;
    private String metricType;
    private BigDecimal metricValue;
    private BigDecimal thresholdValue;
    private String level;
    private String status;
    private String message;
    private LocalDateTime triggerTime;
    private LocalDateTime ackTime;
    private String ackUser;
    private LocalDateTime createTime;
}
