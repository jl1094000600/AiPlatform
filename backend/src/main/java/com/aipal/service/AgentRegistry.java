package com.aipal.service;

import com.aipal.dto.A2AMessage;
import com.aipal.entity.AiAgent;
import com.aipal.entity.AiAgentVersion;
import com.aipal.mapper.AiAgentMapper;
import com.aipal.mapper.AiAgentVersionMapper;
import com.aipal.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Slf4j
@Service
public class AgentRegistry {

    private final AiAgentMapper agentMapper;
    private final AiAgentVersionMapper versionMapper;
    private final ChatModelService chatModelService;
    private final A2AMessageService a2aMessageService;

    private final Map<String, AgentContext> registeredAgents = new ConcurrentHashMap<>();

    public AgentRegistry(AiAgentMapper agentMapper,
                         AiAgentVersionMapper versionMapper,
                         ChatModelService chatModelService,
                         A2AMessageService a2aMessageService) {
        this.agentMapper = agentMapper;
        this.versionMapper = versionMapper;
        this.chatModelService = chatModelService;
        this.a2aMessageService = a2aMessageService;
    }

    public void refreshAgents() {
        String prefix = TenantContext.tenantId() + ":";
        registeredAgents.keySet().removeIf(key -> key.startsWith(prefix));
        List<AiAgent> agents = agentMapper.selectList(
            new LambdaQueryWrapper<AiAgent>().eq(AiAgent::getStatus, 1)
        );

        for (AiAgent agent : agents) {
            try {
                registerAgent(agent);
            } catch (Exception e) {
                log.error("Failed to register agent: {}", agent.getAgentCode(), e);
            }
        }
        log.info("Loaded {} online agents", agents.size());
    }

    public void registerAgent(AiAgent agent) {
        AiAgentVersion latestVersion = versionMapper.selectOne(
            new LambdaQueryWrapper<AiAgentVersion>()
                .eq(AiAgentVersion::getAgentId, agent.getId())
                .eq(AiAgentVersion::getStatus, 1)
                .orderByDesc(AiAgentVersion::getPublishTime)
                .last("LIMIT 1")
        );

        ChatClient chatClient = null;
        if (agent.getApiUrl() != null && !agent.getApiUrl().isEmpty()) {
            String modelCode = extractModelCode(agent);
            if (modelCode != null) {
                chatClient = chatModelService.getChatClient(modelCode);
            }
        }

        AgentContext context = new AgentContext();
        context.setAgent(agent);
        context.setVersion(latestVersion);
        context.setChatClient(chatClient);
        context.setHandler(createAgentHandler(agent));

        registeredAgents.put(cacheKey(agent.getAgentCode()), context);

        a2aMessageService.registerHandler(agent.getAgentCode(), context.getHandler());

        log.info("Registered agent: {} [{}]", agent.getAgentCode(), agent.getAgentName());
    }

    private String extractModelCode(AiAgent agent) {
        if (agent.getRequestSchema() != null) {
            try {
                Map<String, Object> schema = parseSchema(agent.getRequestSchema());
                if (schema.containsKey("modelCode")) {
                    return schema.get("modelCode").toString();
                }
            } catch (Exception e) {
                log.debug("Failed to extract modelCode from agent schema", e);
            }
        }
        return "default";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSchema(String schema) {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        try {
            return mapper.readValue(schema, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Function<A2AMessage, A2AMessage> createAgentHandler(AiAgent agent) {
        return message -> {
            A2AMessage response = new A2AMessage();
            response.setSourceAgent(agent.getAgentCode());
            response.setTargetAgent(message.getSourceAgent());
            response.setSessionId(message.getSessionId());
            response.setAction(A2AMessage.Action.respond);

            try {
                if (message.getPayload() != null && message.getPayload().containsKey("intent")) {
                    String intent = message.getPayload().get("intent").toString();
                    Object result = processAgentIntent(agent, intent, message.getPayload());
                    response.setPayload(Map.of("status", "success", "result", result));
                } else {
                    response.setPayload(Map.of("status", "success", "message", "Agent acknowledged"));
                }
            } catch (Exception e) {
                response.setPayload(Map.of("status", "error", "error", e.getMessage()));
            }

            return response;
        };
    }

    private Object processAgentIntent(AiAgent agent, String intent, Map<String, Object> params) {
        AgentContext ctx = registeredAgents.get(cacheKey(agent.getAgentCode()));
        if (ctx != null && ctx.getChatClient() != null) {
            String result = ctx.getChatClient().prompt()
                .user(intent)
                .call()
                .content();
            return Map.of("response", result);
        }
        return Map.of("response", "Agent " + agent.getAgentName() + " processed: " + intent);
    }

    public AgentContext getAgent(String agentCode) {
        String key = cacheKey(agentCode);
        AgentContext context = registeredAgents.get(key);
        if (context == null) {
            AiAgent agent = agentMapper.selectOne(new LambdaQueryWrapper<AiAgent>()
                    .eq(AiAgent::getAgentCode, agentCode)
                    .eq(AiAgent::getStatus, 1)
                    .last("LIMIT 1"));
            if (agent != null) {
                registerAgent(agent);
                context = registeredAgents.get(key);
            }
        }
        return context;
    }

    public List<AgentContext> getAllAgents() {
        String prefix = TenantContext.tenantId() + ":";
        List<AgentContext> agents = registeredAgents.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .toList();
        if (agents.isEmpty()) {
            refreshAgents();
            agents = registeredAgents.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(prefix))
                    .map(Map.Entry::getValue)
                    .toList();
        }
        return agents;
    }

    public boolean isAgentRegistered(String agentCode) {
        return registeredAgents.containsKey(cacheKey(agentCode));
    }

    public void unregisterAgent(String agentCode) {
        registeredAgents.remove(cacheKey(agentCode));
        log.info("Unregistered agent: {}", agentCode);
    }

    private String cacheKey(String agentCode) {
        return TenantContext.tenantId() + ":" + agentCode;
    }

    @lombok.Data
    public static class AgentContext {
        private AiAgent agent;
        private AiAgentVersion version;
        private ChatClient chatClient;
        private Function<A2AMessage, A2AMessage> handler;
    }
}
