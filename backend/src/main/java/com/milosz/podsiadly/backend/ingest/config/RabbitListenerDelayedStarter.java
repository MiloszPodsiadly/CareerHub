package com.milosz.podsiadly.backend.ingest.config;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
class RabbitListenerDelayedStarter {
    private final TaskScheduler taskScheduler;
    private final ObjectProvider<RabbitListenerEndpointRegistry> registryProvider;

    @EventListener(ApplicationReadyEvent.class)
    public void startLater() {
        taskScheduler.schedule(() -> {
            var reg = registryProvider.getIfAvailable();
            if (reg == null) return;
            var c = reg.getListenerContainer("jobUrlConsumer");
            if (c != null && !c.isRunning()) c.start();
        }, Instant.now().plus(Duration.ofMinutes(3)));
    }
}

