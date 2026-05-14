package com.aipal.agent.intent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.aipal.agent.intent")
@MapperScan("com.aipal.agent.intent.mapper")
@EnableScheduling
public class IntentAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntentAgentApplication.class, args);
    }
}
