package com.aipal.agent.marketing;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.aipal.agent.marketing", "com.aipal.marketing"})
@MapperScan({"com.aipal.agent.marketing.mapper", "com.aipal.marketing.mapper"})
@EnableScheduling
public class MarketingAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketingAgentApplication.class, args);
    }
}
