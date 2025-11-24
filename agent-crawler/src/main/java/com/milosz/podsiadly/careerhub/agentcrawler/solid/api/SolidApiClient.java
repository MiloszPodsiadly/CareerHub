package com.milosz.podsiadly.careerhub.agentcrawler.solid.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

@Slf4j
@Component
@RequiredArgsConstructor
public class SolidApiClient {

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

        try {
            ResponseEntity<String> response =
                    restTemplate.getForEntity(sitemapUrl, String.class);

            log.info("[solid-api] sitemap status={} contentType={}",
                    response.getStatusCode(), response.getHeaders().getContentType());

            String body = response.getBody();
            if (body == null || body.isBlank()) {
                log.warn("[solid-api] sitemap body is null/blank");
                return Set.of();
            }

            return extractOfferUrls(body);

        } catch (Exception e) {
            log.warn("[solid-api] failed to fetch sitemap: {}", e.toString(), e);
            return Set.of();
        }
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
}
