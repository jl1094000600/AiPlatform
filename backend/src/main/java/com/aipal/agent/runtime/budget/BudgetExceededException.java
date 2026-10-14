package com.aipal.agent.runtime.budget;

/**
 * Raised when a run exceeds one of its budgets (tokens, steps, time, child tasks).
 * Carries a short, content-free reason that is safe to persist on the run.
 */
public class BudgetExceededException extends RuntimeException {

    private final String code;

    public BudgetExceededException(String code, String reason) {
        super(reason);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
