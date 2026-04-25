package com.aipal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 心跳请求DTO
 */
@Data
public class HeartbeatRequest {
    @NotBlank(message = "agentCode 不能为空")
    private String agentCode;

    /** 实例ID，默认 "default" */
    private String instanceId;

    /** 健康评分 0-100 */
    @Min(0) @Max(100)
    private Integer healthScore;

    /** 当前健康端点 */
    private String endpoint;

    /** 额外元数据 */
    private Map<String, Object> metadata;

    // 保留兼容性字段
    private Long agentId;
    private java.util.List<String> capabilities;
}
