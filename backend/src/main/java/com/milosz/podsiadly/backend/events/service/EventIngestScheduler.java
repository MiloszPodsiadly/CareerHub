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

    @Scheduled(initialDelayString = "PT3M", fixedDelayString = "PT1000000D")
    public void runOnceAfterStartup() {
        log.info("[events.ingest] startup run");
        svc.runAll();
    }

    @Scheduled(initialDelayString = "PT12H", fixedDelayString = "PT12H")
    public void runEvery12Hours() {
        log.info("[events.ingest] scheduled 12h run");
        svc.runAll();
    }
}
