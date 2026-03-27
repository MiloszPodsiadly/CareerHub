package com.milosz.podsiadly.backend.ingest.config;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RequiredArgsConstructor
class RabbitListenerDelayedStarter {
    private final TaskScheduler taskScheduler;
    private final ObjectProvider<RabbitListenerEndpointRegistry> registryProvider;

    @EventListener(ApplicationReadyEvent.class)
    public void startLater() {
        taskScheduler.schedule(() -> {
            var reg = registryProvider.getIfAvailable();
            if (reg == null) return;
            startIfNeeded(reg, "justjoinJobUrlConsumer");
            startIfNeeded(reg, "nfjJobUrlConsumer");
            startIfNeeded(reg, "solidJobUrlConsumer");
            startIfNeeded(reg, "theProtocolJobUrlConsumer");
        }, Instant.now().plus(Duration.ofMinutes(3)));
    }

    private static void startIfNeeded(RabbitListenerEndpointRegistry registry, String listenerId) {
        var container = registry.getListenerContainer(listenerId);
        if (container != null && !container.isRunning()) {
            container.start();
            log.info("[amqp] started listener={}", listenerId);
        }
    }
}

