package com.aipal.agent.image;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ImageAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImageAgentApplication.class, args);
    }
}
