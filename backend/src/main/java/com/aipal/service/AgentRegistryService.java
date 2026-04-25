package com.aipal.service;

import com.aipal.dto.AgentRegisterRequest;
import com.aipal.dto.AgentRegisterResponse;
import com.aipal.entity.AgentRegistration;

import java.util.List;

/**
 * Agent注册服务接口
 * 支持Push（推送注册）和Pull（平台探测）两种模式
 */
public interface AgentRegistryService {

    /**
     * Push模式注册Agent
     * @param request 注册请求
     * @return 注册响应
     */
    AgentRegisterResponse register(AgentRegisterRequest request);

    /**
     * 注销Agent
     * @param agentCode Agent编码
     * @param instanceId 实例ID
     */
    void unregister(String agentCode, String instanceId);

    /**
     * Pull模式探测Agent健康状态
     * @param agentCode Agent编码
     * @param instanceId 实例ID
     * @return 是否在线
     */
    boolean probeAgent(String agentCode, String instanceId);

    /**
     * 获取Agent注册信息
     * @param agentCode Agent编码
     * @param instanceId 实例ID
     * @return 注册信息
     */
    AgentRegistration getRegistration(String agentCode, String instanceId);

    /**
     * 获取所有注册的Agent
     * @return 注册列表
     */
    List<AgentRegistration> getAllRegistrations();

    /**
     * 刷新注册列表（从数据库加载）
     */
    void refreshRegistrations();

    /**
     * 更新Agent状态
     * @param agentCode Agent编码
     * @param instanceId 实例ID
     * @param status 新状态
     */
    void updateStatus(String agentCode, String instanceId, Integer status);
}
