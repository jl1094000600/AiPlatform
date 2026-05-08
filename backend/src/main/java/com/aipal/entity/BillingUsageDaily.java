package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("billing_usage_daily")
public class BillingUsageDaily {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate usageDate;
    private Long agentId;
    private Long customerId;
    private Long bizModuleId;
    private Long totalCalls;
    private Long totalTokens;
    private BigDecimal totalCost;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
