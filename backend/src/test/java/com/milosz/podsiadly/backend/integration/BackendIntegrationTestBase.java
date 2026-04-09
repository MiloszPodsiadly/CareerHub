package com.milosz.podsiadly.backend.integration;

import com.milosz.podsiadly.backend.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public abstract class BackendIntegrationTestBase {

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("eureka.client.register-with-eureka", () -> "false");
        registry.add("eureka.client.fetch-registry", () -> "false");
        registry.add("spring.task.scheduling.enabled", () -> "false");
        registry.add("ingest.scheduler.enabled", () -> "false");
        registry.add("app.mailer.enabled", () -> "false");
        registry.add("app.frontend.url", () -> "http://frontend.test");
        registry.add("app.platform.base-url", () -> "http://frontend.test");
    }
}
