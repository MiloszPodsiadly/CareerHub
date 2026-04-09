package com.milosz.podsiadly.careerhub.agentcrawler.ingest.service;

import com.milosz.podsiadly.careerhub.agentcrawler.job.domain.JobSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestService {

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36";

    private final IngestPublisher publisher;

    public long ingestSitemap(String sitemapUrl, JobSource source) throws Exception {
        return switch (source) {
            case JUSTJOIN -> ingestXmlSitemapRecursiveWithCounter(sitemapUrl, source);
            case NOFLUFFJOBS, SOLIDJOBS, THEPROTOCOL, PRACUJ -> {
                log.info("[ingest] {} sitemap ingest is handled by a dedicated agent scheduler, skip url={}",
                        source, sitemapUrl);
                yield 0L;
            }
        };
    }

    private long ingestXmlSitemapRecursiveWithCounter(String url, JobSource source) throws Exception {
        AtomicLong counter = new AtomicLong(0);
        ingestXmlSitemapRecursive(url, source, counter);
        long total = counter.get();
        log.info("[ingest] sitemap={} source={} totalUrlsEnqueued={}", url, source, total);
        return total;
    }

    private void ingestXmlSitemapRecursive(String url, JobSource source, AtomicLong counter) throws Exception {
        log.debug("[ingest] fetching sitemap url={} source={}", url, source);

        Document doc;
        try {
            doc = Jsoup.connect(url)
                    .userAgent(BROWSER_UA)
                    .ignoreContentType(true)
                    .timeout(15_000)
                    .get();
        } catch (HttpStatusException ex) {
            if (source == JobSource.JUSTJOIN && ex.getStatusCode() == 403) {
                log.warn("[ingest] JUSTJOIN sitemap blocked with HTTP 403, skip url={}", url);
                return;
            }
            throw ex;
        }

        if (!doc.select("sitemapindex").isEmpty()) {
            for (Element loc : doc.select("sitemap > loc")) {
                String child = loc.text().trim();
                if (!child.isBlank()) {
                    ingestXmlSitemapRecursive(child, source, counter);
                }
            }
            return;
        }

        for (Element loc : doc.select("url > loc, loc")) {
            String jobUrl = loc.text().trim();
            if (jobUrl.isEmpty()) {
                continue;
            }

            publisher.publishUrl(jobUrl, source);
            long current = counter.incrementAndGet();
            if (current % 1000 == 0) {
                log.debug("[ingest] {} urls enqueued so far (source={})", current, source);
            }
        }
    }
}
