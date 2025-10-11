package com.milosz.podsiadly.backend.job.service;

import com.milosz.podsiadly.backend.job.domain.ArchiveReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class OfferRetentionScheduler {

    private final OfferArchiveService archiveService;

    @Scheduled(cron = "*/30 * * * * *")
    public void sweep() {
        archiveService.archiveInactiveOlderThan(Duration.ofSeconds(30), ArchiveReason.RETENTION);
    }
}
