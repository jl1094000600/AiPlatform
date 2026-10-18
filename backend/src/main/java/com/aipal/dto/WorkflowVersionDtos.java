package com.aipal.dto;

import java.time.LocalDateTime;

public final class WorkflowVersionDtos {
    private WorkflowVersionDtos() {
    }

    public record VersionView(Long id, Long workflowId, String workflowCode, String workflowName,
                              Integer versionNo, String definitionHash, String status,
                              Long publishedBy, LocalDateTime publishTime) {
    }

    public record VersionDetail(Long id, Long workflowId, Integer versionNo,
                                String definitionJson, String layoutJson, String definitionHash,
                                String status, Long publishedBy, LocalDateTime publishTime,
                                String workflowCode, String workflowName) {
    }

    public record PublishRequest(String definitionJson, String layoutJson) {
    }
}
