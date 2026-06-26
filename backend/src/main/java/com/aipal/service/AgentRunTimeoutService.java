package com.aipal.service;

import com.aipal.agent.runtime.AgentRunStatus;
import com.aipal.entity.AgentRun;
import com.aipal.mapper.AgentRunMapper;
import com.aipal.security.TenantContext;
import com.aipal.security.TenantTaskRunner;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** Fails closed after the P0 execution wall-clock budget and prevents queued work from continuing. */
@Service
@RequiredArgsConstructor
public class AgentRunTimeoutService {
    private final AgentRunMapper runMapper;
    private final AgentTaskService taskService;
    private final TenantTaskRunner tenantTaskRunner;
    private final AgentRunEventService eventService;

    @Scheduled(fixedDelayString = "${aipal.agent-runtime.timeout-scan-delay-ms:30000}",
            initialDelayString = "${aipal.agent-runtime.timeout-scan-initial-delay-ms:60000}")
    public void timeoutExpiredRunsForAllTenants() {
        tenantTaskRunner.forEachActiveTenant("agent-run-timeout", tenant -> timeoutExpiredRuns(600));
    }

    public int timeoutExpiredRuns(int maxRunSeconds) {
        if (maxRunSeconds < 10) throw new IllegalArgumentException("maxRunSeconds must be at least 10");
        LocalDateTime now = LocalDateTime.now();
        List<AgentRun> expired = runMapper.selectList(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getTenantId, TenantContext.tenantId())
                .eq(AgentRun::getStatus, AgentRunStatus.RUNNING.name())
                .lt(AgentRun::getStartTime, now.minusSeconds(maxRunSeconds)));
        int timedOut = 0;
        for (AgentRun run : expired) {
            AgentRun update = new AgentRun();
            update.setStatus(AgentRunStatus.TIMEOUT.name());
            update.setErrorMessage("Execution timed out");
            update.setEndTime(now);
            update.setVersion(run.getVersion() + 1);
            update.setUpdateTime(now);
            int changed = runMapper.update(update, new LambdaUpdateWrapper<AgentRun>()
                    .eq(AgentRun::getId, run.getId()).eq(AgentRun::getTenantId, TenantContext.tenantId())
                    .eq(AgentRun::getStatus, AgentRunStatus.RUNNING.name()));
            if (changed == 1) {
                taskService.cancelTasksForRun(run.getId(), "Execution timed out");
                eventService.record(run, AgentRunStatus.RUNNING.name(), AgentRunStatus.TIMEOUT.name(), "Execution timed out");
                timedOut++;
            }
        }
        return timedOut;
    }
}
