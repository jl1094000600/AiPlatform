package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_evaluation_criteria")
public class AiEvaluationCriteria {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String criteriaCode;
    private String criteriaName;
    private String description;
    private String type;
    private String formula;
    private Double weight;
    private String thresholds;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}