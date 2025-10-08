package com.milosz.podsiadly.backend.ingest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestService {

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36";

    private final IngestPublisher publisher;

    public long ingestSitemap(String sitemapUrl, String source) throws Exception {
        return ingestSitemapRecursive(sitemapUrl, source);
    }

    private long ingestSitemapRecursive(String url, String source) throws Exception {
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
                    total += ingestSitemapRecursive(child, source);
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
