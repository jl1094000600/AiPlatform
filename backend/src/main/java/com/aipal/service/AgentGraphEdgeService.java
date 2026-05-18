package com.aipal.service;

import com.aipal.dto.AgentGraphEdgeEvaluation;
import com.aipal.dto.AgentGraphEdgeRequest;
import com.aipal.entity.AgentGraphEdgeConfig;
import com.aipal.entity.AgentHeartbeat;
import com.aipal.entity.AiAgent;
import com.aipal.mapper.AgentGraphEdgeConfigMapper;
import com.aipal.mapper.AgentHeartbeatMapper;
import com.aipal.mapper.AiAgentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AgentGraphEdgeService {
    private final AgentGraphEdgeConfigMapper edgeMapper;
    private final AiAgentMapper agentMapper;
    private final AgentHeartbeatMapper heartbeatMapper;

    public List<AgentGraphEdgeConfig> listEdges() {
        return edgeMapper.selectList(
                new LambdaQueryWrapper<AgentGraphEdgeConfig>()
                        .eq(AgentGraphEdgeConfig::getEnabled, 1)
                        .orderByDesc(AgentGraphEdgeConfig::getUpdateTime)
        );
    }

    public AgentGraphEdgeEvaluation evaluate(AgentGraphEdgeRequest request) {
        AiAgent source = requireAgent(request.getSourceAgentId(), "sourceAgentId");
        AiAgent target = requireAgent(request.getTargetAgentId(), "targetAgentId");
        return evaluate(source, target, request);
    }

    @Transactional
    public AgentGraphEdgeConfig createEdge(AgentGraphEdgeRequest request) {
        AiAgent source = requireAgent(request.getSourceAgentId(), "sourceAgentId");
        AiAgent target = requireAgent(request.getTargetAgentId(), "targetAgentId");
        AgentGraphEdgeEvaluation evaluation = evaluate(source, target, request);

        AgentGraphEdgeConfig existing = edgeMapper.selectOne(
                new LambdaQueryWrapper<AgentGraphEdgeConfig>()
                        .eq(AgentGraphEdgeConfig::getSourceAgentId, source.getId())
                        .eq(AgentGraphEdgeConfig::getTargetAgentId, target.getId())
                        .eq(AgentGraphEdgeConfig::getEdgeType, valueOrDefault(request.getEdgeType(), evaluation.getRecommendedEdgeType()))
                        .eq(AgentGraphEdgeConfig::getTriggerIntent, valueOrDefault(request.getTriggerIntent(), evaluation.getRecommendedTriggerIntent()))
        );

        AgentGraphEdgeConfig edge = existing != null ? existing : new AgentGraphEdgeConfig();
        edge.setSourceAgentId(source.getId());
        edge.setSourceAgentCode(source.getAgentCode());
        edge.setTargetAgentId(target.getId());
        edge.setTargetAgentCode(target.getAgentCode());
        edge.setEdgeType(valueOrDefault(request.getEdgeType(), evaluation.getRecommendedEdgeType()));
        edge.setTriggerIntent(valueOrDefault(request.getTriggerIntent(), evaluation.getRecommendedTriggerIntent()));
        edge.setConditionExpression(request.getConditionExpression());
        edge.setParamMapping(valueOrDefault(request.getParamMapping(), "{}"));
        edge.setTimeoutSeconds(request.getTimeoutSeconds() != null ? request.getTimeoutSeconds() : 30);
        edge.setRetryCount(request.getRetryCount() != null ? request.getRetryCount() : 0);
        edge.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        edge.setSuitabilityLevel(evaluation.getLevel());
        edge.setSuitabilityScore(evaluation.getScore());
        edge.setSuitabilityMessage(evaluation.getMessage());
        edge.setUpdateTime(LocalDateTime.now());

        if (edge.getId() == null) {
            edge.setCreateTime(LocalDateTime.now());
            edgeMapper.insert(edge);
        } else {
            edgeMapper.updateById(edge);
        }
        return edge;
    }

    @Transactional
    public void deleteEdge(Long edgeId) {
        edgeMapper.deleteById(edgeId);
    }

    private AgentGraphEdgeEvaluation evaluate(AiAgent source, AiAgent target, AgentGraphEdgeRequest request) {
        if (source.getId().equals(target.getId())) {
            throw new IllegalArgumentException("\u4e0d\u80fd\u5c06 Agent \u8fde\u63a5\u5230\u81ea\u5df1");
        }

        List<String> reasons = new ArrayList<>();
        int score = 45;

        String sourceText = agentText(source);
        String targetText = agentText(target);
        boolean sourceIsIntent = containsAny(sourceText, "intent", "route", "classifier", "\u610f\u56fe", "\u8def\u7531");
        boolean targetIsIntent = containsAny(targetText, "intent", "route", "classifier", "\u610f\u56fe", "\u8def\u7531");
        boolean targetIsBusiness = containsAny(targetText, "marketing", "sales", "image", "tts", "analysis", "agent",
                "\u8425\u9500", "\u9500\u552e", "\u56fe\u50cf", "\u8bed\u97f3");

        if (sourceIsIntent && !targetIsIntent) {
            score += 35;
            reasons.add("\u6e90 Agent \u5177\u6709\u610f\u56fe\u8bc6\u522b\u6216\u8def\u7531\u80fd\u529b\uff0c\u76ee\u6807 Agent \u53ef\u4ee5\u627f\u63a5\u4e1a\u52a1\u8c03\u7528\u3002");
        } else if (sourceIsIntent) {
            score += 18;
            reasons.add("\u6e90 Agent \u53ef\u4ee5\u8fdb\u884c\u8def\u7531\uff0c\u4f46\u76ee\u6807 Agent \u4e5f\u50cf\u8def\u7531\u7c7b\u8282\u70b9\uff0c\u5efa\u8bae\u786e\u8ba4\u4e1a\u52a1\u8fb9\u754c\u3002");
        } else if (targetIsBusiness) {
            score += 18;
            reasons.add("\u76ee\u6807 Agent \u5177\u6709\u4e1a\u52a1\u80fd\u529b\uff0c\u53ef\u4ee5\u63a5\u6536\u59d4\u6258\u4efb\u52a1\u3002");
        } else {
            reasons.add("\u5f53\u524d Agent \u5143\u6570\u636e\u4e2d\u672a\u53d1\u73b0\u660e\u663e\u7684\u8bed\u4e49\u5339\u914d\u3002");
        }

        if (sameCategory(source, target)) {
            score += 10;
            reasons.add("\u4e24\u4e2a Agent \u5c5e\u4e8e\u76f8\u540c\u5206\u7c7b\u3002");
        }

        if (isOnline(source)) {
            score += 5;
            reasons.add("\u6e90 Agent \u5f53\u524d\u5b58\u5728\u5728\u7ebf\u5fc3\u8df3\u3002");
        } else {
            score -= 5;
            reasons.add("\u6e90 Agent \u5f53\u524d\u4e0d\u5728\u7ebf\u3002");
        }

        if (isOnline(target)) {
            score += 5;
            reasons.add("\u76ee\u6807 Agent \u5f53\u524d\u5b58\u5728\u5728\u7ebf\u5fc3\u8df3\u3002");
        } else {
            score -= 5;
            reasons.add("\u76ee\u6807 Agent \u5f53\u524d\u4e0d\u5728\u7ebf\u3002");
        }

        score = Math.max(0, Math.min(100, score));
        String level = score >= 75 ? "HIGH" : score >= 50 ? "MEDIUM" : "LOW";

        AgentGraphEdgeEvaluation evaluation = new AgentGraphEdgeEvaluation();
        evaluation.setSuitable(score >= 50);
        evaluation.setLevel(level);
        evaluation.setScore(score);
        evaluation.setReasons(reasons);
        evaluation.setRecommendedEdgeType(sourceIsIntent ? "ROUTE" : "SEQUENCE");
        evaluation.setRecommendedTriggerIntent(recommendTriggerIntent(source, target, request));
        evaluation.setMessage(switch (level) {
            case "HIGH" -> "\u63a8\u8350\u5173\u8054\uff0c\u8be5\u8fde\u7ebf\u5f88\u9002\u5408\u4f5c\u4e3a\u4e1a\u52a1\u8def\u7531\u3002";
            case "MEDIUM" -> "\u53ef\u7528\u5173\u8054\uff0c\u8fd0\u884c\u524d\u5efa\u8bae\u786e\u8ba4\u610f\u56fe\u548c\u53c2\u6570\u6620\u5c04\u3002";
            default -> "\u5173\u8054\u8f83\u5f31\uff0c\u4ecd\u53ef\u8fde\u63a5\uff0c\u4f46\u5f53\u524d\u5143\u6570\u636e\u672a\u663e\u793a\u6e05\u6670\u5339\u914d\u5173\u7cfb\u3002";
        });
        return evaluation;
    }

    private AiAgent requireAgent(Long agentId, String fieldName) {
        if (agentId == null) {
            throw new IllegalArgumentException(fieldName + " \u4e0d\u80fd\u4e3a\u7a7a");
        }
        AiAgent agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("\u672a\u627e\u5230 Agent: " + agentId);
        }
        return agent;
    }

    private boolean isOnline(AiAgent agent) {
        List<AgentHeartbeat> heartbeats = heartbeatMapper.selectList(
                new LambdaQueryWrapper<AgentHeartbeat>()
                        .eq(AgentHeartbeat::getAgentCode, agent.getAgentCode())
        );
        if (heartbeats.isEmpty()) {
            heartbeats = heartbeatMapper.selectList(
                    new LambdaQueryWrapper<AgentHeartbeat>()
                            .eq(AgentHeartbeat::getAgentId, agent.getId())
            );
        }
        if (heartbeats.isEmpty()) {
            return false;
        }
        return "online".equals(AgentRuntimeStatusSupport.runtimeStatus(
                AgentRuntimeStatusSupport.latestHeartbeat(heartbeats)));
    }

    private String recommendTriggerIntent(AiAgent source, AiAgent target, AgentGraphEdgeRequest request) {
        if (request.getTriggerIntent() != null && !request.getTriggerIntent().isBlank()) {
            return request.getTriggerIntent();
        }
        String targetText = agentText(target);
        if (containsAny(targetText, "marketing", "sales", "\u8425\u9500", "\u9500\u552e")) {
            return "marketing_analysis";
        }
        if (containsAny(targetText, "image", "vision", "\u56fe\u50cf", "\u56fe\u7247")) {
            return "image_recognition";
        }
        if (containsAny(targetText, "tts", "voice", "audio", "\u8bed\u97f3")) {
            return "tts_synthesis";
        }
        return "agent_route";
    }

    private boolean sameCategory(AiAgent source, AiAgent target) {
        return source.getCategory() != null
                && target.getCategory() != null
                && source.getCategory().equalsIgnoreCase(target.getCategory());
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String agentText(AiAgent agent) {
        return String.join(" ",
                valueOrDefault(agent.getAgentCode(), ""),
                valueOrDefault(agent.getAgentName(), ""),
                valueOrDefault(agent.getCategory(), ""),
                valueOrDefault(agent.getDescription(), "")
        ).toLowerCase(Locale.ROOT);
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value : defaultValue;
    }
}
