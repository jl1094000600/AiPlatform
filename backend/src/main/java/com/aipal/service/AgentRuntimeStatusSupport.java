package com.aipal.service;

import com.aipal.entity.AgentHeartbeat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

final class AgentRuntimeStatusSupport {

    static final Duration DEFAULT_HEARTBEAT_TIMEOUT = Duration.ofSeconds(90);

    private AgentRuntimeStatusSupport() {
    }

    static AgentHeartbeat latestHeartbeat(List<AgentHeartbeat> heartbeats) {
        if (heartbeats == null || heartbeats.isEmpty()) {
            return null;
        }
        return heartbeats.stream()
                .filter(h -> h.getLastHeartbeat() != null)
                .max(Comparator.comparing(AgentHeartbeat::getLastHeartbeat))
                .orElse(heartbeats.get(0));
    }

    static boolean isOnline(AgentHeartbeat heartbeat) {
        return isOnline(heartbeat, LocalDateTime.now(), DEFAULT_HEARTBEAT_TIMEOUT);
    }

    static boolean isOnline(AgentHeartbeat heartbeat, LocalDateTime now, Duration timeout) {
        return heartbeat != null
                && heartbeat.getStatus() != null
                && heartbeat.getStatus() == 1
                && heartbeat.getLastHeartbeat() != null
                && Duration.between(heartbeat.getLastHeartbeat(), now).compareTo(timeout) <= 0;
    }

    static String runtimeStatus(AgentHeartbeat heartbeat) {
        if (isOnline(heartbeat)) {
            return "online";
        }
        if (heartbeat != null && heartbeat.getStatus() != null && heartbeat.getStatus() == 3) {
            return "error";
        }
        return "offline";
    }

    static int onlineInstanceCount(List<AgentHeartbeat> heartbeats) {
        if (heartbeats == null || heartbeats.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        return (int) heartbeats.stream()
                .filter(h -> isOnline(h, now, DEFAULT_HEARTBEAT_TIMEOUT))
                .count();
    }
}
