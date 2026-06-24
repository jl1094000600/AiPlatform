package com.aipal.service.memory;

import com.aipal.entity.AutomationPipeline;
import com.aipal.entity.AiMemoryProject;
import com.aipal.security.TenantContext;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Holds a project identity that was resolved from a server-side domain object.
 * Request payloads must never populate this context directly.
 */
@Component
public class MemoryTrustedProjectContext {

    private final ThreadLocal<TrustedProject> current = new ThreadLocal<>();

    public ProjectScope openPipeline(AutomationPipeline pipeline) {
        if (pipeline == null || pipeline.getId() == null) {
            throw new IllegalArgumentException("A persisted pipeline is required for project memory context");
        }
        TrustedProject previous = current.get();
        TrustedProject trusted = new TrustedProject(TenantContext.tenantId(), "pipeline:" + pipeline.getId(),
                "AUTOMATION_PIPELINE", pipeline.getInitiatorUserId());
        current.set(trusted);
        return () -> {
            if (previous == null) current.remove();
            else current.set(previous);
        };
    }

    ProjectScope openProject(AiMemoryProject project) {
        if (project == null || project.getId() == null || project.getProjectKey() == null || project.getProjectKey().isBlank()) {
            throw new IllegalArgumentException("An active persisted project is required for memory context");
        }
        TrustedProject previous = current.get();
        current.set(new TrustedProject(project.getTenantId(), project.getProjectKey(), project.getProjectType(), project.getOwnerUserId()));
        return () -> {
            if (previous == null) current.remove();
            else current.set(previous);
        };
    }

    public Optional<TrustedProject> current() {
        return Optional.ofNullable(current.get());
    }

    public record TrustedProject(Long tenantId, String projectKey, String projectType, Long ownerUserId) {
    }

    @FunctionalInterface
    public interface ProjectScope extends AutoCloseable {
        @Override
        void close();
    }
}
