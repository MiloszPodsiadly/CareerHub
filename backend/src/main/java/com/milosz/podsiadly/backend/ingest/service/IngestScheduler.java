package com.milosz.podsiadly.backend.ingest.service;

import com.milosz.podsiadly.backend.ingest.config.IngestSchedulerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestScheduler {

    private final IngestService ingestService;
    private final IngestSchedulerProperties props;
    private final TaskScheduler taskScheduler;

    private final AtomicBoolean startupScheduled = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleFirstRun() {
        if (!props.isEnabled()) {
            log.info("[ingest] scheduler disabled – skipping startup run");
            return;
        }
        if (startupScheduled.compareAndSet(false, true)) {
            var when = Instant.now().plus(props.getInitialDelay());
            log.info("[ingest] scheduling first run at {}", when);
            taskScheduler.schedule(this::safeRunOnce, when);
        }
    }

    @Scheduled(fixedDelayString = "#{@ingestSchedulerProperties.fixedDelay.toMillis()}")
    public void runPeriodic() {
        if (!props.isEnabled()) return;
        safeRunOnce();
    }

    private void safeRunOnce() {
        for (var s : props.getSources()) {
            try {
                long cnt = ingestService.ingestSitemap(s.getSitemap(), s.getSource());
                log.info("[ingest] {} <- {} ({} URLs enqueued)", s.getSource(), s.getSitemap(), cnt);
            } catch (Exception ex) {
                log.warn("[ingest] failed for {} <- {}: {}", s.getSource(), s.getSitemap(), ex.toString());
            }
        }
    }
}
