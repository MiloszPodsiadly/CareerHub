package com.milosz.podsiadly.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean(name = "tcPostgres")
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("testdb")
                .withUsername("app")
                .withPassword("app");
    }

    @Bean(name = "tcRabbit")
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer("rabbitmq:3.13-management")
                .withEnv("RABBITMQ_DEFAULT_USER", "app")
                .withEnv("RABBITMQ_DEFAULT_PASS", "app")
                .waitingFor(
                        Wait.forListeningPorts(5672, 15672)
                                .withStartupTimeout(Duration.ofSeconds(120))
                );
    }
}
