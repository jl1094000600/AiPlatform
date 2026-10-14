package com.aipal.agent.runtime.budget;

import com.aipal.entity.AgentRun;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Single source of truth for run-level budgets: tokens, steps, wall-clock time and child tasks.
 * AgentRunExecutionService reuses this guard instead of keeping a second token gate.
 */
@Component
public class RunBudgetGuard {

    @Value("${aipal.agent-runtime.budget.default-max-duration-seconds:600}")
    private int defaultMaxDurationSeconds = 600;
    @Value("${aipal.agent-runtime.budget.default-max-steps:8}")
    private int defaultMaxSteps = 8;
    @Value("${aipal.agent-runtime.budget.default-max-total-tokens:8000}")
    private int defaultMaxTotalTokens = 8000;
    @Value("${aipal.agent-runtime.budget.default-max-child-tasks:5}")
    private int defaultMaxChildTasks = 5;

    public BudgetLimits limitsFor(AgentRun run) {
        int maxTokens = run == null || run.getMaxTotalTokens() == null ? defaultMaxTotalTokens : run.getMaxTotalTokens();
        int maxSteps = run == null || run.getMaxSteps() == null ? defaultMaxSteps : run.getMaxSteps();
        int maxChildTasks = run == null || run.getMaxChildTasks() == null ? defaultMaxChildTasks : run.getMaxChildTasks();
        int maxDurationSeconds = run == null || run.getMaxDurationSeconds() == null
                ? defaultMaxDurationSeconds : run.getMaxDurationSeconds();
        return new BudgetLimits(maxTokens, maxSteps, maxDurationSeconds, maxChildTasks);
    }

    /** How many loop/tool steps this run may take. */
    public int effectiveMaxSteps(AgentRun run) {
        return Math.max(1, Math.min(limitsFor(run).maxSteps(), 20));
    }

    public Optional<BudgetViolation> check(AgentRun run, int totalTokens, int completedSteps, Duration elapsed) {
        BudgetLimits limits = limitsFor(run);
        if (totalTokens > limits.maxTokens()) {
            return Optional.of(new BudgetViolation("TOKENS", "Token budget exceeded"));
        }
        if (completedSteps > limits.maxSteps()) {
            return Optional.of(new BudgetViolation("STEPS", "Step budget exceeded"));
        }
        if (elapsed != null && elapsed.compareTo(Duration.ofSeconds(limits.maxDurationSeconds())) > 0) {
            return Optional.of(new BudgetViolation("TIME", "Time budget exceeded"));
        }
        return Optional.empty();
    }

    public Optional<BudgetViolation> checkChildTaskCount(AgentRun run, long currentChildTasks) {
        BudgetLimits limits = limitsFor(run);
        if (currentChildTasks >= limits.maxChildTasks()) {
            return Optional.of(new BudgetViolation("CHILD_TASKS", "Child task budget exceeded"));
        }
        return Optional.empty();
    }

    /** Remaining wall-clock budget for this run; empty when no start time is known. */
    public Optional<Duration> remainingTime(AgentRun run) {
        if (run == null || run.getStartTime() == null) return Optional.empty();
        BudgetLimits limits = limitsFor(run);
        Duration elapsed = Duration.between(run.getStartTime(), java.time.LocalDateTime.now());
        Duration max = Duration.ofSeconds(limits.maxDurationSeconds());
        Duration remaining = max.minus(elapsed);
        return Optional.of(remaining.isNegative() ? Duration.ZERO : remaining);
    }

    public void assertWithinBudget(AgentRun run, int totalTokens, int completedSteps, Duration elapsed) {
        check(run, totalTokens, completedSteps, elapsed)
                .ifPresent(violation -> {
                    throw new BudgetExceededException(violation.code(), violation.reason());
                });
    }

    public record BudgetLimits(int maxTokens, int maxSteps, int maxDurationSeconds, int maxChildTasks) {
    }

    public record BudgetViolation(String code, String reason) {
    }
}
