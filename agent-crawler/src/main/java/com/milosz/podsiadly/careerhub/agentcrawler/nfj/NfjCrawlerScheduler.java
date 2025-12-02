package com.milosz.podsiadly.careerhub.agentcrawler.nfj;

import com.milosz.podsiadly.careerhub.agentcrawler.mq.NfjJobPublisher;
import com.milosz.podsiadly.careerhub.agentcrawler.nfj.api.NfjApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class NfjCrawlerScheduler {

    private final NfjApiClient apiClient;
    private final NfjJobPublisher publisher;

    private final AtomicLong totalSentSinceStart = new AtomicLong(0);

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
            initialDelayString = "${agent.nfj.initial-delay-ms:180000}",
            fixedDelayString   = "${agent.nfj.interval-ms:21600000}"
    )
    public void runPeriodic() {
        log.info("[agent-nfj] periodic crawl triggered");
        runOnce();
    }

    private void runOnce() {
        try {
            Set<String> allUrls = new LinkedHashSet<>();

            for (String slug : NFJ_CATEGORY_SLUGS) {
                log.info("[agent-nfj] crawling category slug={} (NFJ /pl/{})", slug, slug);

                Set<String> slice = apiClient.fetchAllJobUrls(slug);

                log.info("[agent-nfj] slug={} got {} urls (before merge)", slug, slice.size());

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
            log.info("NFJ offers fetched & sent this run (after merge) = {}", sentThisRun);
            log.info("NFJ offers sent to queue since start            = {}", total);
            log.info("================================");

        } catch (Exception e) {
            log.error("[agent-nfj] runOnce failed: {}", e.toString(), e);
        }
    }
}
