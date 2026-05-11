package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_skill")
public class AiSkill {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String skillCode;
    private String skillName;
    private String description;
    private Integer status;
    private String promptContent;
    private String functionDefinitions;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
