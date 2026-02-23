package com.milosz.podsiadly.backend.job.service;

import com.milosz.podsiadly.backend.job.domain.JobSource;
import com.milosz.podsiadly.backend.job.repository.JobOfferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${jobs.stale.default-cutoff:PT48H}")
    private Duration defaultStaleCutoff;

    @Value("${jobs.stale.nfj-cutoff:PT72H}")
    private Duration nfjStaleCutoff;

    @Scheduled(cron = "${jobs.stale.cron:0 */10 * * * *}")
    @Transactional
    public void deactivateStale() {
        deactivateFor(JobSource.JUSTJOIN, defaultStaleCutoff);
        deactivateFor(JobSource.SOLIDJOBS, defaultStaleCutoff);
        deactivateFor(JobSource.PLATFORM, defaultStaleCutoff);
        deactivateFor(JobSource.NOFLUFFJOBS, nfjStaleCutoff);
        deactivateFor(JobSource.THEPROTOCOL, defaultStaleCutoff);
        deactivateFor(JobSource.PRACUJ, defaultStaleCutoff);
    }

    private void deactivateFor(JobSource src, Duration cutoffDur) {
        Instant cutoff = Instant.now().minus(cutoffDur);
        int deactivated = offers.deactivateStale(src, cutoff);
        if (deactivated > 0) {
            log.info("[stale-deactivate] {} deactivated={} cutoff={}", src, deactivated, cutoff);
        }
    }
}
