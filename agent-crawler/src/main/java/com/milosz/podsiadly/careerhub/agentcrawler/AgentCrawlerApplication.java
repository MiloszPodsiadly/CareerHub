package com.milosz.podsiadly.careerhub.agentcrawler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AgentCrawlerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentCrawlerApplication.class, args);
    }

}
