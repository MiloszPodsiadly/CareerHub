package com.milosz.podsiadly.careerhub.agentcrawler.theprotocol;

import com.milosz.podsiadly.careerhub.agentcrawler.mq.TheProtocolJobPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class TheProtocolCrawlerScheduler {

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36";

    private final TheProtocolJobPublisher publisher;

    @Value("${agent.theprotocol.sitemap-url}")
    private String sitemapUrl;

    @Value("${agent.theprotocol.contact-email:contact@theprotocol.it}")
    private String contactEmail;

    @Scheduled(
            initialDelayString = "${agent.theprotocol.initial-delay-ms:240000}",
            fixedDelayString   = "${agent.theprotocol.interval-ms:43200000}"
    )
    public void run() {
        Counters c = new Counters();
        Set<String> seen = new HashSet<>(8192);

        try {
            log.info("[theprotocol] fetching sitemap={}", sitemapUrl);

            FetchResult r = fetchXmlOrHtmlOnce(sitemapUrl);

            if (r.blockedByCloudflare) {
                logCloudflareTodo(r);
                return;
            }

            if (r.body == null || r.body.isBlank()) {
                log.warn("[theprotocol] empty sitemap body url={} status={}", sitemapUrl, r.status);
                return;
            }

            ingestSitemapXml(r.body, sitemapUrl, c, seen);

            log.info("[theprotocol] done enqueued={} changedUrls={} duplicatesSkipped={}",
                    c.enqueued, c.changed, c.duplicates);

        } catch (Exception e) {
            log.warn("[theprotocol] sitemap fetch failed url={} err={}", sitemapUrl, e.toString());
        }
    }

    private FetchResult fetchXmlOrHtmlOnce(String url) {
        try {
            Connection.Response res = Jsoup.connect(url)
                    .userAgent(BROWSER_UA)
                    .ignoreContentType(true)
                    .timeout(15_000)
                    .followRedirects(true)
                    .execute();

            int status = res.statusCode();
            String finalUrl = res.url() != null ? res.url().toString() : url;

            Map<String, String> headers = res.headers();
            String ct = header(headers, "content-type");
            String server = header(headers, "server");
            String cfRay = header(headers, "cf-ray");

            String cookieNames = (res.cookies() != null && !res.cookies().isEmpty())
                    ? String.join(",", res.cookies().keySet().stream().sorted().toList())
                    : "";

            String body = res.body();

            boolean blocked = looksLikeCloudflare(body)
                    || (status == 403 && (server != null && server.toLowerCase().contains("cloudflare")))
                    || (status == 403 && ct != null && ct.toLowerCase().contains("text/html"));

            String title = extractTitleBestEffort(body);

            return new FetchResult(url, finalUrl, status, title, ct, server, cfRay, cookieNames, body, blocked, null);

        } catch (HttpStatusException e) {
            return new FetchResult(url, url, e.getStatusCode(), "", "", "", "", "", "", e.getStatusCode() == 403, e.toString());
        } catch (Exception e) {
            return new FetchResult(url, url, -1, "", "", "", "", "", "", false, e.toString());
        }
    }

    private void ingestSitemapXml(String xml, String baseUrl, Counters c, Set<String> seen) {
        Document doc = Jsoup.parse(xml, baseUrl, Parser.xmlParser());

        if (!doc.select("sitemapindex").isEmpty()) {
            int children = doc.select("sitemap > loc").size();
            log.warn("[theprotocol] sitemapindex detected children={} (recursion disabled to avoid extra requests)", children);
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

    private void logCloudflareTodo(FetchResult r) {
        String head = r.body == null ? "" : r.body.substring(0, Math.min(350, r.body.length()));

        log.warn("[theprotocol] BLOCKED_BY_CLOUDFLARE status={} url={} finalUrl={} title='{}' ct={} server={} cf-ray={} cookieNames={} ts={}",
                r.status,
                r.url,
                r.finalUrl,
                sanitize(r.title),
                r.contentType,
                r.server,
                r.cfRay,
                r.cookieNames,
                Instant.now().toString()
        );

        log.warn("[theprotocol] TODO: contact theprotocol.it via email={} to allowlist/bypass Cloudflare for sitemap. Provide cf-ray and timestamp.",
                contactEmail
        );

        log.warn("[theprotocol] bodyHead={}", oneLine(head));
    }

    private static String header(Map<String, String> headers, String keyLower) {
        if (headers == null || headers.isEmpty()) return "";
        for (var e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(keyLower)) return e.getValue();
        }
        return "";
    }

    private static boolean looksLikeCloudflare(String body) {
        if (body == null) return true;
        String b = body.toLowerCase();
        return b.contains("just a moment")
                || b.contains("cdn-cgi/challenge-platform")
                || b.contains("enable javascript and cookies")
                || b.contains("cf_chl_opt")
                || b.contains("__cf_bm")
                || (b.contains("<title>") && (b.contains("just a moment") || b.contains("cierpliwości")));
    }

    private static String extractTitleBestEffort(String body) {
        if (body == null || body.isBlank()) return "";
        try {
            Document d = Jsoup.parse(body);
            String t = d.title();
            return t != null ? t : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String oneLine(String s) {
        if (s == null) return "";
        return s.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        return s.replace("\n", " ").replace("\r", " ").trim();
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

    private record FetchResult(
            String url,
            String finalUrl,
            int status,
            String title,
            String contentType,
            String server,
            String cfRay,
            String cookieNames,
            String body,
            boolean blockedByCloudflare,
            String error
    ) {}

    private static final class Counters {
        long enqueued = 0;
        long changed = 0;
        long duplicates = 0;
    }
}
