package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("automation_deploy_profile")
public class AutomationDeployProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String profileName;
    private String deployType;
    private String environmentName;
    private Integer status;
    private String buildCommand;
    private String testCommand;
    private String healthCheckUrl;
    private Integer timeoutSeconds;
    private String dockerConfig;
    private String jenkinsConfig;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
