package com.milosz.podsiadly.careerhub.agentcrawler.nfj.api;

import com.google.common.util.concurrent.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class NfjApiClient {

    private static final String BASE_URL = "https://nofluffjobs.com";
    private static final String SEARCH_PATH = "/api/search/posting";
    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final RateLimiter SEARCH_RATE_LIMITER = RateLimiter.create(0.5d);

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 2_000L;
    private static final long MAX_BACKOFF_MS = 30_000L;
    private static final double BACKOFF_MULTIPLIER = 2.0d;

    private final RestTemplate restTemplate;

    public Set<String> fetchAllJobUrls(String categorySlug) {
        return fetchAllJobUrls(categorySlug, new LinkedHashSet<>());
    }

    public Set<String> fetchAllJobUrls(String categorySlug, Set<String> seenIdsThisRun) {
        Set<String> urls = new LinkedHashSet<>();

        int pageTo = 0;
        int totalPages = 1;

        while (pageTo < totalPages) {
            NfjSearchResponse response = fetchPage(categorySlug, pageTo, DEFAULT_PAGE_SIZE);

            if (response == null ||
                    response.getPostings() == null ||
                    response.getPostings().isEmpty()) {
                log.info("[nfj-api] empty page {} – stopping (category={})", pageTo, categorySlug);
                break;
            }

            response.getPostings().forEach(p -> {
                String id  = p.getId();
                String url = p.getUrl();

                if (id == null || url == null || url.isBlank()) {
                    log.debug("[nfj-api] skip posting with missing id/url (category={})", categorySlug);
                    return;
                }

                if (seenIdsThisRun != null && !seenIdsThisRun.add(id)) {
                    log.debug("[nfj-api] skip duplicate posting id={} (already seen in this run)", id);
                    return;
                }

                String abs = "https://nofluffjobs.com/pl/job/" + url;
                urls.add(abs);
            });

            log.info("[nfj-api] page={} collected so far={} (category={})",
                    pageTo, urls.size(), categorySlug);

            if (response.getTotalPages() > 0) {
                totalPages = response.getTotalPages();
            }
            pageTo++;
        }

        log.info("[nfj-api] DONE: total unique urls={} (category={})", urls.size(), categorySlug);
        return urls;
    }

    private NfjSearchResponse fetchPage(String categorySlug, int pageTo, int pageSize) {
        log.info("[nfj-api] fetching pageTo={} pageSize={} categorySlug={}",
                pageTo, pageSize, categorySlug);

        URI uri = UriComponentsBuilder
                .fromHttpUrl(BASE_URL + SEARCH_PATH)
                .queryParam("withSalaryMatch", true)
                .queryParam("pageTo", pageTo)
                .queryParam("pageSize", pageSize)
                .queryParam("salaryCurrency", "PLN")
                .queryParam("salaryPeriod", "month")
                .queryParam("region", "pl")
                .queryParam("language", "pl-PL")
                .build(true)
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/infiniteSearch+json"));
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        NfjSearchRequest body = NfjSearchRequest.forCategorySlug(categorySlug, pageSize);
        HttpEntity<NfjSearchRequest> entity = new HttpEntity<>(body, headers);

        long backoffMs = INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            SEARCH_RATE_LIMITER.acquire();

            try {
                ResponseEntity<NfjSearchResponse> resp = restTemplate.exchange(
                        uri,
                        HttpMethod.POST,
                        entity,
                        NfjSearchResponse.class
                );

                HttpStatusCode status = resp.getStatusCode();

                if (status.is2xxSuccessful()) {
                    return resp.getBody();
                }

                int code = status.value();
                if (code == 429 || status.is5xxServerError()) {
                    log.warn("[nfj-api] transient HTTP {} for pageTo={} (category={}), attempt {}/{}",
                            code, pageTo, categorySlug, attempt, MAX_ATTEMPTS);

                    if (attempt == MAX_ATTEMPTS) {
                        log.warn("[nfj-api] giving up after {} attempts for pageTo={} (category={})",
                                MAX_ATTEMPTS, pageTo, categorySlug);
                        return null;
                    }

                    sleepQuietly(backoffMs);
                    backoffMs = Math.min((long) (backoffMs * BACKOFF_MULTIPLIER), MAX_BACKOFF_MS);
                    continue;
                }

                log.warn("[nfj-api] non-2xx status={} for pageTo={} (category={}), not retrying",
                        status, pageTo, categorySlug);
                return null;

            } catch (RestClientException ex) {
                log.warn("[nfj-api] RestClientException for pageTo={} (category={}), attempt {}/{}: {}",
                        pageTo, categorySlug, attempt, MAX_ATTEMPTS, ex.toString());

                if (attempt == MAX_ATTEMPTS) {
                    log.warn("[nfj-api] giving up after {} attempts for pageTo={} (category={})",
                            MAX_ATTEMPTS, pageTo, categorySlug);
                    return null;
                }

                sleepQuietly(backoffMs);
                backoffMs = Math.min((long) (backoffMs * BACKOFF_MULTIPLIER), MAX_BACKOFF_MS);
            }
        }

        return null;
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("[nfj-api] sleep interrupted during backoff");
        }
    }
}
