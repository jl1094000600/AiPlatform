package com.aipal.agent.runtime.budget;

import com.aipal.entity.AgentRun;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunBudgetGuardTest {

    private final RunBudgetGuard guard = new RunBudgetGuard();

    @Test
    void tokenBudgetExceededIsReported() {
        AgentRun run = run(100, 8, 600);
        Optional<RunBudgetGuard.BudgetViolation> violation = guard.check(run, 101, 1, Duration.ofSeconds(1));

        assertTrue(violation.isPresent());
        assertEquals("TOKENS", violation.get().code());
        assertEquals("Token budget exceeded", violation.get().reason());
    }

    @Test
    void stepBudgetExceededIsReported() {
        AgentRun run = run(8000, 3, 600);
        Optional<RunBudgetGuard.BudgetViolation> violation = guard.check(run, 10, 4, Duration.ofSeconds(1));

        assertTrue(violation.isPresent());
        assertEquals("STEPS", violation.get().code());
        assertEquals("Step budget exceeded", violation.get().reason());
    }

    @Test
    void timeBudgetExceededIsReported() {
        AgentRun run = run(8000, 8, 5);
        Optional<RunBudgetGuard.BudgetViolation> violation = guard.check(run, 10, 1, Duration.ofSeconds(6));

        assertTrue(violation.isPresent());
        assertEquals("TIME", violation.get().code());
        assertEquals("Time budget exceeded", violation.get().reason());
    }

    @Test
    void withinBudgetPasses() {
        AgentRun run = run(8000, 8, 600);
        assertTrue(guard.check(run, 100, 2, Duration.ofSeconds(10)).isEmpty());
    }

    @Test
    void assertWithinBudgetThrowsBudgetExceededException() {
        AgentRun run = run(50, 8, 600);
        BudgetExceededException exception = assertThrows(BudgetExceededException.class,
                () -> guard.assertWithinBudget(run, 51, 1, Duration.ofSeconds(1)));
        assertEquals("TOKENS", exception.code());
        assertEquals("Token budget exceeded", exception.getMessage());
    }

    @Test
    void childTaskBudgetExceededIsReported() {
        AgentRun run = run(8000, 8, 600);
        run.setMaxChildTasks(2);
        assertTrue(guard.checkChildTaskCount(run, 2).isPresent());
        assertTrue(guard.checkChildTaskCount(run, 1).isEmpty());
    }

    @Test
    void assertBeforeModelCallBlocksWhenTokensExhausted() {
        AgentRun run = run(50, 8, 600);
        BudgetExceededException exception = assertThrows(BudgetExceededException.class,
                () -> guard.assertBeforeModelCall(run, 51, 1, Duration.ofSeconds(1)));
        assertEquals("TOKENS", exception.code());
    }

    @Test
    void assertBeforeModelCallAllowsNextCallWithinBudget() {
        AgentRun run = run(50, 8, 600);
        guard.assertBeforeModelCall(run, 50, 1, Duration.ofSeconds(1));
    }

    @Test
    void assertBeforeToolCallBlocksWhenChildTasksExhausted() {
        AgentRun run = run(8000, 8, 600);
        run.setMaxChildTasks(2);
        BudgetExceededException exception = assertThrows(BudgetExceededException.class,
                () -> guard.assertBeforeToolCall(run, 10, 1, Duration.ofSeconds(1), 2));
        assertEquals("CHILD_TASKS", exception.code());
    }

    @Test
    void assertBeforeToolCallBlocksWhenTimeExhausted() {
        AgentRun run = run(8000, 8, 5);
        BudgetExceededException exception = assertThrows(BudgetExceededException.class,
                () -> guard.assertBeforeToolCall(run, 10, 1, Duration.ofSeconds(6), 0));
        assertEquals("TIME", exception.code());
    }

    private AgentRun run(int maxTokens, int maxSteps, int maxDurationSeconds) {
        AgentRun run = new AgentRun();
        run.setId(1L);
        run.setMaxTotalTokens(maxTokens);
        run.setMaxSteps(maxSteps);
        run.setMaxDurationSeconds(maxDurationSeconds);
        run.setStartTime(LocalDateTime.now());
        return run;
    }
}
