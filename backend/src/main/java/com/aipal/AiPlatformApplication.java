package com.aipal;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.aipal.**.mapper")
@EnableScheduling
public class AiPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiPlatformApplication.class, args);
    }
}
