package com.aipal.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AutomationCommandGuardTest {

    @Test
    void allowsSingleWhitelistedBuildCommand() {
        assertDoesNotThrow(() -> AutomationCommandGuard.requireSafeCommand("mvn test", "buildCommand"));
        assertDoesNotThrow(() -> AutomationCommandGuard.requireSafeCommand("npm run build", "buildCommand"));
    }

    @Test
    void rejectsShellOperatorsAndShellExecutables() {
        assertThrows(IllegalArgumentException.class,
                () -> AutomationCommandGuard.requireSafeCommand("npm test && powershell whoami", "testCommand"));
        assertThrows(IllegalArgumentException.class,
                () -> AutomationCommandGuard.requireSafeCommand("npm test & whoami", "testCommand"));
        assertThrows(IllegalArgumentException.class,
                () -> AutomationCommandGuard.requireSafeCommand("cmd.exe /c whoami", "testCommand"));
    }
}
