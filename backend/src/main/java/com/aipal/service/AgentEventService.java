package com.aipal.service;

import com.aipal.entity.AgentRegistration;
import com.aipal.entity.AgentRegistrationEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent事件通知服务接口
 */
public interface AgentEventService {

    /**
     * 发布Agent注册事件
     * @param registration 注册信息
     */
    void publishRegisterEvent(AgentRegistration registration);

    /**
     * 发布Agent注销事件
     * @param registration 注册信息
     */
    void publishUnregisterEvent(AgentRegistration registration);

    /**
     * 发布心跳超时事件
     * @param agentCode Agent编码
     * @param instanceId 实例ID
     */
    void publishHeartbeatTimeoutEvent(String agentCode, String instanceId);

    /**
     * 发布Agent状态变更事件
     * @param agentCode Agent编码
     * @param instanceId 实例ID
     * @param previousStatus 变更前状态
     * @param currentStatus 变更后状态
     */
    void publishStatusChangeEvent(String agentCode, String instanceId,
                                   Integer previousStatus, Integer currentStatus);

    /**
     * 获取Agent事件历史
     * @param agentCode Agent编码
     * @param instanceId 实例ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 事件列表
     */
    List<AgentRegistrationEvent> getEventHistory(String agentCode, String instanceId,
                                                  LocalDateTime startTime, LocalDateTime endTime);
}
