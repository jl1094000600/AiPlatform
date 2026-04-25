package com.aipal.service;

import com.aipal.dto.HeartbeatRequest;
import com.aipal.entity.AgentHeartbeat;

import java.util.List;

/**
 * 心跳管理服务接口
 */
public interface HeartbeatManagementService {

    /**
     * 记录心跳
     * @param request 心跳请求
     */
    void recordHeartbeat(HeartbeatRequest request);

    /**
     * 检测离线Agent（定时任务）
     */
    void detectOfflineAgents();

    /**
     * 检查Agent是否在线
     * @param agentCode Agent编码
     * @param instanceId 实例ID
     * @return 是否在线
     */
    boolean isAgentOnline(String agentCode, String instanceId);

    /**
     * 获取Agent心跳信息
     * @param agentCode Agent编码
     * @param instanceId 实例ID
     * @return 心跳信息
     */
    AgentHeartbeat getHeartbeat(String agentCode, String instanceId);

    /**
     * 获取Agent所有实例的心跳
     * @param agentCode Agent编码
     * @return 心跳列表
     */
    List<AgentHeartbeat> getAgentInstances(String agentCode);
}
