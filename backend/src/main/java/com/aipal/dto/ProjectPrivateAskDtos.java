package com.aipal.dto;

import java.util.List;

/**
 * Frozen M3-C contract for zero-persistence Private Ask and explicit draft conversion.
 */
public final class ProjectPrivateAskDtos {
    private ProjectPrivateAskDtos() {
    }

    public record PrivateAskRequest(String question, List<ProjectModelGatewayDtos.PrivateAskTurn> history) {
        public PrivateAskRequest {
            history = history == null ? List.of() : List.copyOf(history);
        }
    }

    public record PrivateAskResponse(String answer, List<ProjectModelGatewayDtos.PrivateAskSource> sources,
                                     List<String> limitations) {
        public PrivateAskResponse {
            sources = sources == null ? List.of() : List.copyOf(sources);
            limitations = limitations == null ? List.of() : List.copyOf(limitations);
        }
    }

    public record ConvertPrivateAskRequest(String title, String description, Long suggestedAgentId,
                                           Long suggestedAssigneeUserId, String idempotencyKey) {
    }
}
