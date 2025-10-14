package com.milosz.podsiadly.backend.events.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @EnableScheduling @RequiredArgsConstructor
public class EventIngestScheduler {
    private final EventIngestService svc;

    //@Scheduled(cron = "0 */3  * * * *", zone = "Europe/Warsaw") // bulk raz dziennie
    //public void bulkDaily() { svc.runAll(); }

    @Scheduled(cron = "0 */3 * * * *", zone = "Europe/Warsaw") // meetup częściej
    public void meetupOften() { svc.runAll(); }
}
