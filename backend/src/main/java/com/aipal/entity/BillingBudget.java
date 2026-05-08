package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("billing_budget")
public class BillingBudget {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String budgetName;
    private String scopeType;
    private Long scopeId;
    private BigDecimal amount;
    private BigDecimal alertThreshold;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
