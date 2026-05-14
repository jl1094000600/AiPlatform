package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("code_quality_rule")
public class CodeQualityRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long standardId;
    private String ruleCode;
    private String category;
    private String severity;
    private String title;
    private String description;
    private String checkPrompt;
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
