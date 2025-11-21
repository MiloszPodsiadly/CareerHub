package com.milosz.podsiadly.backend.ingest.service;

import com.milosz.podsiadly.backend.job.domain.JobSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

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
            case JUSTJOIN -> ingestXmlSitemapRecursive(sitemapUrl, source);

            case NOFLUFFJOBS -> {
                log.info("[ingest] NFJ sitemap ingest is handled by agent-crawler – backend returns 0 (url={})", sitemapUrl);
                yield 0L;
            }

            default -> ingestXmlSitemapRecursive(sitemapUrl, source);
        };
    }

    private long ingestXmlSitemapRecursive(String url, JobSource source) throws Exception {
        Document doc = Jsoup.connect(url)
                .userAgent(BROWSER_UA)
                .ignoreContentType(true)
                .timeout(15000)
                .get();

        if (!doc.select("sitemapindex").isEmpty()) {
            long total = 0;
            for (Element loc : doc.select("sitemap > loc")) {
                String child = loc.text().trim();
                if (!child.isBlank()) {
                    total += ingestXmlSitemapRecursive(child, source);
                }
            }
            return total;
        }

        long count = 0;
        for (Element loc : doc.select("url > loc, loc")) {
            String jobUrl = loc.text().trim();
            if (jobUrl.isEmpty()) continue;
            publisher.publishUrl(jobUrl, source);
            count++;
        }
        return count;
    }
}
