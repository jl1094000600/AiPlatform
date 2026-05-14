package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("code_quality_standard")
public class CodeQualityStandard {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String standardCode;
    private String standardName;
    private String description;
    private String language;
    private String framework;
    private Integer status;
    private String gateConfig;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
