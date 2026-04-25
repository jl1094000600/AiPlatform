package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Agent注册信息实体类
 * 支持Push（推送注册）和Pull（平台探测）两种注册模式
 */
@Data
@TableName("ai_agent_registration")
public class AgentRegistration {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** Agent唯一编码 */
    private String agentCode;

    /** Agent名称 */
    private String agentName;

    /** 能力描述 */
    private String description;

    /** 分类 */
    private String category;

    /** 注册方式: PUSH(推送注册) / PULL(平台探测) */
    private String registryType;

    /** Agent API地址 */
    private String apiUrl;

    /** 健康检查端点（默认 /health） */
    private String healthEndpoint;

    /** 请求Schema */
    private String requestSchema;

    /** 响应Schema */
    private String responseSchema;

    /** 实例ID（用于多实例部署） */
    private String instanceId;

    /** 心跳间隔（秒），默认30 */
    private Integer heartbeatInterval;

    /** 心跳超时（秒），默认90 */
    private Integer heartbeatTimeout;

    /** 状态: 0-待激活, 1-在线, 2-离线, 3-已注销 */
    private Integer status;

    /** 所有者ID */
    private Long ownerId;

    /** 最后心跳时间 */
    private LocalDateTime lastHeartbeat;

    /** 注册时间 */
    private LocalDateTime registeredTime;

    /** 注销时间 */
    private LocalDateTime unregisteredTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
