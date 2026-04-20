package com.milosz.podsiadly.careerhub.agentcrawler.solid.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

@Slf4j
@Component
@RequiredArgsConstructor
public class SolidApiClient {

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36";
    private static final int MAX_SITEMAP_ATTEMPTS = 4;
    private static final long INITIAL_BACKOFF_MS = 2_000L;
    private static final long MAX_BACKOFF_MS = 20_000L;

    private final RestTemplate restTemplate;

    @Value("${agent.solid.base-url:https://solid.jobs}")
    private String baseUrl;

    @Value("${agent.solid.sitemap-url:https://solid.jobs/sitemap.xml}")
    private String sitemapUrl;

    public Set<String> fetchOfferUrlsFromSitemap() {
        return fetchOfferUrlsFromSitemap(sitemapUrl);
    }

    public Set<String> fetchOfferUrlsFromSitemap(String sitemapUrl) {
        log.info("[solid-api] fetching sitemap from {}", sitemapUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(
                MediaType.APPLICATION_XML,
                MediaType.TEXT_XML,
                MediaType.TEXT_HTML,
                MediaType.ALL
        ));
        headers.setAcceptLanguageAsLocales(List.of(java.util.Locale.forLanguageTag("pl-PL"), java.util.Locale.ENGLISH));
        headers.set("User-Agent", BROWSER_UA);
        headers.set("Referer", baseUrl + "/");
        headers.set("Cache-Control", "no-cache");
        headers.set("Pragma", "no-cache");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        long backoffMs = INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= MAX_SITEMAP_ATTEMPTS; attempt++) {
            long startedAt = System.currentTimeMillis();
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        sitemapUrl,
                        HttpMethod.GET,
                        entity,
                        String.class
                );

                log.info("[solid-api] sitemap attempt={}/{} status={} contentType={} tookMs={}",
                        attempt,
                        MAX_SITEMAP_ATTEMPTS,
                        response.getStatusCode(),
                        response.getHeaders().getContentType(),
                        System.currentTimeMillis() - startedAt);

                String body = response.getBody();
                if (body == null || body.isBlank()) {
                    log.warn("[solid-api] sitemap body is null/blank attempt={}/{}", attempt, MAX_SITEMAP_ATTEMPTS);
                    return Set.of();
                }

                return extractOfferUrls(body);
            } catch (HttpStatusCodeException ex) {
                int status = ex.getStatusCode().value();
                String bodySnippet = abbreviateBody(ex.getResponseBodyAsString());
                boolean retryable = status == 429 || status == 503 || status >= 500;

                log.warn("[solid-api] sitemap HTTP {} attempt={}/{} tookMs={} retryable={} body={}",
                        status,
                        attempt,
                        MAX_SITEMAP_ATTEMPTS,
                        System.currentTimeMillis() - startedAt,
                        retryable,
                        bodySnippet);

                if (!retryable || attempt == MAX_SITEMAP_ATTEMPTS) {
                    log.warn("[solid-api] sitemap fetch exhausted after status={} attempts={}", status, attempt, ex);
                    return Set.of();
                }

                sleepWithJitter(backoffMs);
                backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
            } catch (Exception e) {
                log.warn("[solid-api] sitemap fetch failed attempt={}/{} tookMs={} error={}",
                        attempt,
                        MAX_SITEMAP_ATTEMPTS,
                        System.currentTimeMillis() - startedAt,
                        e.toString(),
                        e);

                if (attempt == MAX_SITEMAP_ATTEMPTS) {
                    return Set.of();
                }

                sleepWithJitter(backoffMs);
                backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
            }
        }

        return Set.of();
    }

    public String fetchOfferJsonByOfferUrl(String offerUrl) {
        String path = extractOfferPath(offerUrl);
        return fetchOfferJsonByPath(path);
    }

    public String fetchOfferJsonByPath(String offerPath) {
        String apiUrl = baseUrl + "/api/offers/" + offerPath;

        log.debug("[solid-api] fetching offer JSON from {}", apiUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.solidjobs.jobofferdetails+json, application/json, */*");
        headers.set("X-Requested-With", "XMLHttpRequest");
        headers.set("Referer", baseUrl + "/offer/" + offerPath);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> resp = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (!resp.getStatusCode().is2xxSuccessful()) {
                log.warn("[solid-api] non-2xx status={} for {}", resp.getStatusCode(), apiUrl);
                return null;
            }

            return resp.getBody();
        } catch (Exception e) {
            log.warn("[solid-api] failed to fetch offer JSON {}: {}", apiUrl, e.toString(), e);
            return null;
        }
    }

    private String extractOfferPath(String offerUrl) {
        if (offerUrl == null) return null;
        int idx = offerUrl.indexOf("/offer/");
        if (idx < 0) {
            return offerUrl.replaceFirst("^/+", "");
        }
        return offerUrl.substring(idx + "/offer/".length());
    }

    private Set<String> extractOfferUrls(String xml) {
        Set<String> urls = new LinkedHashSet<>();

        try {
            if (xml.startsWith("\uFEFF")) {
                log.debug("[solid-api] stripping BOM (U+FEFF) from sitemap XML");
                xml = xml.substring(1);
            }

            int firstTagIndex = xml.indexOf('<');
            if (firstTagIndex > 0) {
                xml = xml.substring(firstTagIndex);
            } else if (firstTagIndex < 0) {
                log.warn("[solid-api] no '<' in sitemap XML – cannot parse");
                return urls;
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            var builder = factory.newDocumentBuilder();
            Document doc = builder.parse(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))
            );

            NodeList locs = doc.getElementsByTagName("loc");
            for (int i = 0; i < locs.getLength(); i++) {
                String loc = locs.item(i).getTextContent().trim();
                if (loc.startsWith(baseUrl + "/offer/")) {
                    urls.add(loc);
                }
            }

            log.info("[solid-api] sitemap parsed, offers={}", urls.size());

        } catch (Exception e) {
            log.warn("[solid-api] failed to parse sitemap: {}", e.toString(), e);
        }

        return urls;
    }

    private static void sleepWithJitter(long baseMs) {
        long jitter = ThreadLocalRandom.current().nextLong(250L, 1250L);
        long sleepMs = Math.max(250L, baseMs + jitter);
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static String abbreviateBody(String body) {
        if (body == null) {
            return null;
        }
        String normalized = body
                .replace("\r", " ")
                .replace("\n", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() <= 240) {
            return normalized;
        }
        return normalized.substring(0, 240);
    }
}
