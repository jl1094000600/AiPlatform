package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user_tenant")
public class SysUserTenant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long tenantId;
    private String tenantRole;
    private Integer defaultTenant;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
