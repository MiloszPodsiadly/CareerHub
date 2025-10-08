// src/main/java/com/milosz/podsiadly/backend/ingest/config/SchedulingConfig.java
package com.milosz.podsiadly.backend.ingest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean
    TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler t = new ThreadPoolTaskScheduler();
        t.setPoolSize(2);
        t.setThreadNamePrefix("ingest-scheduler-");
        t.initialize();
        return t;
    }
}
