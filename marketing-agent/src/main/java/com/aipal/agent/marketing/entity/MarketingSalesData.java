package com.aipal.agent.marketing.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("marketing_sales_data")
public class MarketingSalesData {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate salesDate;
    private String region;
    private String productCode;
    private String productName;
    private BigDecimal salesAmount;
    private Integer salesQuantity;
    private BigDecimal profitAmount;
    private String dataSourceConfig;
    private LocalDateTime createTime;
    @TableLogic
    private Integer isDeleted;
}
