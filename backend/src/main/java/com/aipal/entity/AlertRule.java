package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("alert_rule")
public class AlertRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleName;
    private String metricType;
    private String operator;
    private BigDecimal thresholdValue;
    private String level;
    private String notifyChannel;
    private String notifyTarget;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
