package com.milosz.podsiadly.backend.job.service;

import com.milosz.podsiadly.backend.job.domain.JobSource;
import com.milosz.podsiadly.backend.job.repository.JobOfferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
@Slf4j
@Component
@RequiredArgsConstructor
public class OfferStaleDeactivationScheduler {

    private final JobOfferRepository offers;

    private static final Duration DEFAULT_STALE_CUTOFF = Duration.ofHours(48);
    private static final Duration NFJ_STALE_CUTOFF = Duration.ofHours(72);

    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void deactivateStale() {
        deactivateFor(JobSource.JUSTJOIN, DEFAULT_STALE_CUTOFF);
        deactivateFor(JobSource.SOLIDJOBS, DEFAULT_STALE_CUTOFF);
        deactivateFor(JobSource.PLATFORM, DEFAULT_STALE_CUTOFF);
        deactivateFor(JobSource.NOFLUFFJOBS, NFJ_STALE_CUTOFF);
    }

    private void deactivateFor(JobSource src, Duration cutoffDur) {
        Instant cutoff = Instant.now().minus(cutoffDur);
        int deactivated = offers.deactivateStale(src, cutoff);
        if (deactivated > 0) {
            log.info("[stale-deactivate] {} deactivated={} cutoff={}", src, deactivated, cutoff);
        }
    }
}