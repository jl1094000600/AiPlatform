package com.aipal.agent.runtime.workspace;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Isolated work directory for controlled coding tools and test execution.
 */
public interface Workspace extends AutoCloseable {

    Path workDir();

    /**
     * Execute a command inside the workspace. Implementations must enforce the timeout
     * and kill the process tree when it expires.
     */
    CommandResult exec(List<String> command, Duration timeout);

    void putFile(String relPath, String content) throws IOException;

    String getFile(String relPath) throws IOException;

    String health();

    @Override
    void close() throws IOException;
}
