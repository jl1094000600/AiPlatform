package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
@TableName("ai_model")
public class AiModel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String modelCode;
    private String modelName;
    private String provider;
    private String modelVersion;
    private String endpoint;
    private String apiVersion;
    private BigDecimal pricePer1kToken;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
