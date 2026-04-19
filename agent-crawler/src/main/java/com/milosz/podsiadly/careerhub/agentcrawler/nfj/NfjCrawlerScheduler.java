package com.milosz.podsiadly.careerhub.agentcrawler.nfj;

import com.milosz.podsiadly.careerhub.agentcrawler.config.IngestMessagingProperties;
import com.milosz.podsiadly.careerhub.agentcrawler.mq.NfjJobPublisher;
import com.milosz.podsiadly.careerhub.agentcrawler.nfj.api.NfjApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class NfjCrawlerScheduler {

    private final NfjApiClient apiClient;
    private final NfjJobPublisher publisher;
    private final AmqpAdmin amqpAdmin;
    private final IngestMessagingProperties messagingProperties;

    private final AtomicLong totalSentSinceStart = new AtomicLong(0);

    @Value("${jobs.ingest.nfj.max-url-backlog-before-skip:5000}")
    private long maxUrlBacklogBeforeSkip;

    @Value("${jobs.ingest.nfj.max-external-offers-backlog-before-skip:5000}")
    private long maxExternalOffersBacklogBeforeSkip;

    private static final String[] NFJ_CATEGORY_SLUGS = {
            "artificial-intelligence",
            "sys-administrator",
            "business-analyst",
            "backend",
            "data",
            "frontend",
            "fullstack",
            "mobile",
            "architecture",
            "ux",
            "devops",
            "erp",
            "embedded",
            "game-dev",
            "project-manager",
            "security",
            "support",
            "testing",
            "other"
    };

    @Scheduled(
            initialDelayString = "${agent.nfj.initial-delay-ms:15000}",
            fixedDelayString   = "${agent.nfj.interval-ms:86400000}"
    )
    public void runPeriodic() {
        log.info("[agent-nfj] periodic crawl triggered");
        if (shouldSkipDueToBacklog()) {
            return;
        }
        runOnce();
    }

    private void runOnce() {
        try {
            Set<String> allUrls = new LinkedHashSet<>();

            Set<String> seenIdsThisRun = new LinkedHashSet<>();

            for (String slug : NFJ_CATEGORY_SLUGS) {
                log.info("[agent-nfj] crawling category slug={} (NFJ /pl/{})", slug, slug);

                Set<String> slice = apiClient.fetchAllJobUrls(slug, seenIdsThisRun);

                log.info("[agent-nfj] slug={} got {} urls (after id-dedupe, before merge)", slug, slice.size());

                allUrls.addAll(slice);
            }

            log.info("[agent-nfj] NFJ merged unique urls across all slugs={}", allUrls.size());

            int sentThisRun = 0;
            for (String url : allUrls) {
                publisher.publishUrl(url);
                sentThisRun++;
            }

            long total = totalSentSinceStart.addAndGet(sentThisRun);

            log.info("====== NFJ RUN COMPLETE ======");
            log.info("NFJ offers fetched & sent this run (after merge+id-dedupe) = {}", sentThisRun);
            log.info("NFJ offers sent to queue since start                      = {}", total);
            log.info("================================");

        } catch (Exception e) {
            log.error("[agent-nfj] runOnce failed: {}", e.toString(), e);
        }
    }

    private boolean shouldSkipDueToBacklog() {
        Map<String, Long> backlog = currentBacklogSnapshot();
        long urlBacklog = sum(backlog,
                messagingProperties.urlsQueue("NOFLUFFJOBS"),
                messagingProperties.urlsRetry1Queue("NOFLUFFJOBS"),
                messagingProperties.urlsRetry5Queue("NOFLUFFJOBS"),
                messagingProperties.urlsRetry30Queue("NOFLUFFJOBS"));
        long externalOffersBacklog = sum(backlog,
                messagingProperties.externalOffersQueue("NOFLUFFJOBS"),
                messagingProperties.externalOffersRetry1Queue("NOFLUFFJOBS"),
                messagingProperties.externalOffersRetry5Queue("NOFLUFFJOBS"),
                messagingProperties.externalOffersRetry30Queue("NOFLUFFJOBS"));

        boolean skip = urlBacklog >= maxUrlBacklogBeforeSkip
                || externalOffersBacklog >= maxExternalOffersBacklogBeforeSkip;

        if (skip) {
            log.warn("[agent-nfj] skipping run because backlog is too high urlBacklog={} externalOffersBacklog={} snapshot={}",
                    urlBacklog, externalOffersBacklog, backlog);
        } else {
            log.info("[agent-nfj] backlog check OK urlBacklog={} externalOffersBacklog={}",
                    urlBacklog, externalOffersBacklog);
        }

        return skip;
    }

    private Map<String, Long> currentBacklogSnapshot() {
        Map<String, Long> backlog = new LinkedHashMap<>();
        readQueueDepth(backlog, messagingProperties.urlsQueue("NOFLUFFJOBS"));
        readQueueDepth(backlog, messagingProperties.urlsRetry1Queue("NOFLUFFJOBS"));
        readQueueDepth(backlog, messagingProperties.urlsRetry5Queue("NOFLUFFJOBS"));
        readQueueDepth(backlog, messagingProperties.urlsRetry30Queue("NOFLUFFJOBS"));
        readQueueDepth(backlog, messagingProperties.externalOffersQueue("NOFLUFFJOBS"));
        readQueueDepth(backlog, messagingProperties.externalOffersRetry1Queue("NOFLUFFJOBS"));
        readQueueDepth(backlog, messagingProperties.externalOffersRetry5Queue("NOFLUFFJOBS"));
        readQueueDepth(backlog, messagingProperties.externalOffersRetry30Queue("NOFLUFFJOBS"));
        return backlog;
    }

    private void readQueueDepth(Map<String, Long> backlog, String queueName) {
        PropertiesSnapshot props = queueDepth(queueName);
        backlog.put(queueName, props.messageCount());
    }

    private PropertiesSnapshot queueDepth(String queueName) {
        try {
            QueueProperties properties = new QueueProperties(amqpAdmin.getQueueProperties(queueName));
            return new PropertiesSnapshot(properties.messageCount());
        } catch (Exception ex) {
            log.warn("[agent-nfj] cannot read queue depth for {}: {}", queueName, ex.toString());
            return new PropertiesSnapshot(0L);
        }
    }

    private long sum(Map<String, Long> backlog, String... queueNames) {
        long total = 0L;
        for (String queueName : queueNames) {
            total += backlog.getOrDefault(queueName, 0L);
        }
        return total;
    }

    private record PropertiesSnapshot(long messageCount) {
    }

    private record QueueProperties(Properties raw) {
        private static final String QUEUE_MESSAGE_COUNT_KEY = "QUEUE_MESSAGE_COUNT";

        long messageCount() {
            if (raw == null) {
                return 0L;
            }
            Object value = raw.get(QUEUE_MESSAGE_COUNT_KEY);
            if (value instanceof Number number) {
                return number.longValue();
            }
            return 0L;
        }
    }
}
