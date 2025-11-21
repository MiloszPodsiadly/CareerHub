package com.milosz.podsiadly.backend.ingest.service;

import com.milosz.podsiadly.backend.ingest.config.IngestSchedulerProperties;
import com.milosz.podsiadly.backend.job.domain.JobSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestScheduler {

    private final IngestService ingestService;
    private final IngestSchedulerProperties props;

    @Scheduled(
            initialDelayString = "#{@ingestSchedulerProperties.initialDelay.toMillis()}",
            fixedDelayString   = "#{@ingestSchedulerProperties.fixedDelay.toMillis()}"
    )
    public void runPeriodic() {
        if (!props.isEnabled()) {
            log.info("[ingest] scheduler disabled – skipping run");
            return;
        }
        safeRunOnce();
    }

    private void safeRunOnce() {
        for (var s : props.getSources()) {
            try {
                JobSource source = JobSource.valueOf(s.getSource());

                if (source == JobSource.NOFLUFFJOBS) {
                    log.info("[ingest] skipping NFJ in backend scheduler – handled by agent-crawler");
                    continue;
                }

                long cnt = ingestService.ingestSitemap(s.getSitemap(), source);
                log.info("[ingest] {} <- {} ({} URLs enqueued)", source, s.getSitemap(), cnt);
            } catch (Exception ex) {
                log.warn("[ingest] failed for {} <- {}: {}", s.getSource(), s.getSitemap(), ex.toString());
            }
        }
    }
}
