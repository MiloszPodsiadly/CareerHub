package com.milosz.podsiadly.backend.ingest.service;

import com.milosz.podsiadly.backend.job.domain.JobSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

            case NOFLUFFJOBS -> {
                log.info("[ingest] NFJ sitemap ingest is handled by agent-crawler – backend returns 0 (url={})", sitemapUrl);
                yield 0L;
            }

            case SOLIDJOBS -> {
                log.info("[ingest] SolidJobs sitemap ingest is handled by agent-crawler – backend returns 0 (url={})", sitemapUrl);
                yield 0L;
            }

            default -> ingestXmlSitemapRecursiveWithCounter(sitemapUrl, source);
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

        Document doc = Jsoup.connect(url)
                .userAgent(BROWSER_UA)
                .ignoreContentType(true)
                .timeout(15_000)
                .get();

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
