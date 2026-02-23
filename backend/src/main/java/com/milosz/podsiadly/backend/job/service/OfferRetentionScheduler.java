package com.milosz.podsiadly.backend.job.service;

import com.milosz.podsiadly.backend.job.domain.ArchiveReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${jobs.retention.inactive-age:PT30M}")
    private Duration inactiveAge;

    @Scheduled(cron = "${jobs.retention.cron:0 */10 * * * *}")
    public void sweep() {
        archiveService.archiveInactiveOlderThan(inactiveAge, ArchiveReason.RETENTION);
    }
}
