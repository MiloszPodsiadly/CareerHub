package com.milosz.podsiadly.careerhub.agentcrawler.solid;

import com.milosz.podsiadly.careerhub.agentcrawler.mq.SolidJobPublisher;
import com.milosz.podsiadly.careerhub.agentcrawler.solid.api.SolidApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SolidCrawlerScheduler {

    private final SolidApiClient solidApiClient;
    private final SolidJobPublisher publisher;

    @Scheduled(
            initialDelay = 180_000,
            fixedDelay   = 43_200_000
    )
    public void runPeriodic() {
        log.info("[agent-solid] periodic crawl triggered");
        runOnce();
    }

    public void runOnce() {
        try {
            Set<String> urls = solidApiClient.fetchOfferUrlsFromSitemap();

            if (urls.isEmpty()) {
                log.warn("[agent-solid] sitemap returned NO urls");
                return;
            }

            int count = 0;
            for (String url : urls) {
                publisher.publishUrl(url);
                count++;
            }

            log.info("[agent-solid] crawl complete: offers={} published={}", urls.size(), count);

        } catch (Exception e) {
            log.error("[agent-solid] runOnce failed: {}", e.toString(), e);
        }
    }
}
