package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("biz_module")
public class BizModule {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String moduleCode;
    private String moduleName;
    private String description;
    private Long ownerId;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
