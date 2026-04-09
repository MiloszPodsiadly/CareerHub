package com.milosz.podsiadly.backend.events.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class EventIngestScheduler {

    private final EventIngestService svc;

    @Scheduled(initialDelayString = "PT3M", fixedDelayString = "PT24H")
    public void runOnceAfterStartup() {
        log.info("[events.ingest] startup run");
        svc.runAll();
    }

    @Scheduled(initialDelayString = "PT24H", fixedDelayString = "PT24H")
    public void runEvery24Hours() {
        log.info("[events.ingest] scheduled 24h run");
        svc.runAll();
    }
}
