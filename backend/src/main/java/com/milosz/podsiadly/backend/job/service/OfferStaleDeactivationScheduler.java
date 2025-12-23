package com.milosz.podsiadly.backend.job.service;

import com.milosz.podsiadly.backend.job.domain.JobSource;
import com.milosz.podsiadly.backend.job.repository.JobOfferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class OfferStaleDeactivationScheduler {

    private final JobOfferRepository offers;

    private static final Duration NFJ_STALE_CUTOFF = Duration.ofHours(72);

    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void deactivateStaleNofluffJobs() {
        Instant cutoff = Instant.now().minus(NFJ_STALE_CUTOFF);

        int deactivated = offers.deactivateStale(JobSource.NOFLUFFJOBS, cutoff);

        if (deactivated > 0) {
            log.info("[stale-deactivate] NFJ deactivated={} cutoff={}", deactivated, cutoff);
        }
    }
}
