package com.milosz.podsiadly.careerhub.agentcrawler.theprotocol;

import com.milosz.podsiadly.careerhub.agentcrawler.mq.TheProtocolJobPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class TheProtocolCrawlerScheduler {

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36";

    private final TheProtocolJobPublisher publisher;

    @Value("${agent.theprotocol.sitemap-url}")
    private String sitemapUrl;

    @Scheduled(
            initialDelayString = "${agent.theprotocol.initial-delay-ms:240000}",
            fixedDelayString   = "${agent.theprotocol.interval-ms:43200000}"
    )
    public void run() {
        try {
            log.info("[theprotocol] fetching sitemap={}", sitemapUrl);

            Counters c = new Counters();
            Set<String> seen = new HashSet<>(8192);
            ingestSitemapRecursive(sitemapUrl, c, seen);

            log.info("[theprotocol] done enqueued={} changedUrls={} duplicatesSkipped={}",
                    c.enqueued, c.changed, c.duplicates);
        } catch (Exception e) {
            log.warn("[theprotocol] sitemap fetch failed url={} err={}", sitemapUrl, e.toString());
        }
    }

    private void ingestSitemapRecursive(String url, Counters c, Set<String> seen) throws Exception {
        Document doc = Jsoup.connect(url)
                .userAgent(BROWSER_UA)
                .ignoreContentType(true)
                .timeout(15_000)
                .get();

        if (!doc.select("sitemapindex").isEmpty()) {
            for (Element loc : doc.select("sitemap > loc")) {
                String child = loc.text().trim();
                if (child.isBlank()) continue;
                ingestSitemapRecursive(child, c, seen);
            }
            return;
        }

        for (Element loc : doc.select("url > loc")) {
            String original = loc.text().trim();
            if (original.isBlank()) continue;

            String jobUrl = canonicalizeOfferUrl(original);

            if (!jobUrl.equals(original)) {
                c.changed++;
                if (c.changed <= 5) {
                    log.info("[theprotocol] canonicalized: {} -> {}", original, jobUrl);
                }
            }

            if (!seen.add(jobUrl)) {
                c.duplicates++;
                continue;
            }

            publisher.publishUrl(jobUrl);
            c.enqueued++;

            if (c.enqueued % 1000 == 0) {
                log.info("[theprotocol] enqueued {}", c.enqueued);
            }

            if (c.enqueued <= 3 && jobUrl.contains("/szczegoly/praca/")) {
                log.warn("[theprotocol] still /szczegoly/ URL after canonicalize: {}", jobUrl);
            }
        }
    }

    static String canonicalizeOfferUrl(String url) {
        if (url == null) return null;

        String out = url.trim();

        out = out.replace("https://theprotocol.it/szczegoly/praca/", "https://theprotocol.it/praca/");

        if (!out.contains("%2C") && out.contains(",")) {
            out = out.replace(",", "%2C");
        }

        return out;
    }

    private static final class Counters {
        long enqueued = 0;
        long changed = 0;
        long duplicates = 0;
    }
}
