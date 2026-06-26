package com.aipal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AgentRuntimeExecutorConfig {

    @Bean(name = "agentRuntimeTaskExecutor")
    public ThreadPoolTaskExecutor agentRuntimeTaskExecutor(
            TenantContextTaskDecorator taskDecorator,
            @Value("${aipal.agent-runtime.executor.core-size:2}") int corePoolSize,
            @Value("${aipal.agent-runtime.executor.max-size:4}") int maxPoolSize,
            @Value("${aipal.agent-runtime.executor.queue-capacity:20}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("agent-runtime-");
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setTaskDecorator(taskDecorator);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
