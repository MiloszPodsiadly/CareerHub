package com.milosz.podsiadly.careerhub.agentcrawler.nfj;

import com.milosz.podsiadly.careerhub.agentcrawler.mq.NfjJobPublisher;
import com.milosz.podsiadly.careerhub.agentcrawler.nfj.api.NfjApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class NfjCrawlerScheduler {

    private final NfjApiClient apiClient;
    private final NfjJobPublisher publisher;

    @Scheduled(
            initialDelayString = "${agent.nfj.initial-delay-ms:180000}",
            fixedDelayString   = "${agent.nfj.interval-ms:3600000}"
    )
    public void runPeriodic() {
        log.info("[agent-nfj] periodic crawl triggered");
        runOnce();
    }

    private void runOnce() {
        try {
            String keyword = "it";

            Set<String> urls = apiClient.fetchAllJobUrls(keyword);

            int sent = 0;
            for (String url : urls) {
                publisher.publishUrl(url);
                sent++;
            }
            log.info("[agent-nfj] crawl done: collected={} sent={}", urls.size(), sent);

        } catch (Exception e) {
            log.warn("[agent-nfj] runOnce failed: {}", e.toString(), e);
        }
    }
}
