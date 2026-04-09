package com.milosz.podsiadly.careerhub.agentcrawler.justjoin;

import com.milosz.podsiadly.careerhub.agentcrawler.ingest.service.IngestService;
import com.milosz.podsiadly.careerhub.agentcrawler.job.domain.JobSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JustJoinCrawlerScheduler {

    private final IngestService ingestService;

    @Value("${agent.justjoin.sitemap-url}")
    private String sitemapUrl;

    @Scheduled(
            initialDelayString = "${agent.justjoin.initial-delay-ms:15000}",
            fixedDelayString = "${agent.justjoin.interval-ms:86400000}"
    )
    public void runPeriodic() {
        log.info("[agent-justjoin] periodic crawl triggered");
        runOnce();
    }

    public void runOnce() {
        try {
            long count = ingestService.ingestSitemap(sitemapUrl, JobSource.JUSTJOIN);
            log.info("[agent-justjoin] crawl complete: urlsEnqueued={} sitemap={}", count, sitemapUrl);
        } catch (HttpStatusException ex) {
            log.warn("[agent-justjoin] crawl skipped: httpStatus={} sitemap={} err={}",
                    ex.getStatusCode(), sitemapUrl, ex.getMessage());
        } catch (Exception ex) {
            log.error("[agent-justjoin] runOnce failed sitemap={} err={}", sitemapUrl, ex.toString(), ex);
        }
    }
}
