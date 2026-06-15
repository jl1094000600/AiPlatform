package com.aipal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean
    public TenantContextTaskDecorator tenantContextTaskDecorator() {
        return new TenantContextTaskDecorator();
    }

    @Bean(name = {"applicationTaskExecutor", "tenantAwareExecutor"})
    public Executor applicationTaskExecutor(
            TenantContextTaskDecorator taskDecorator,
            @Value("${spring.task.execution.pool.core-size:4}") int corePoolSize,
            @Value("${spring.task.execution.pool.max-size:16}") int maxPoolSize,
            @Value("${spring.task.execution.pool.queue-capacity:500}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("aipal-async-");
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
