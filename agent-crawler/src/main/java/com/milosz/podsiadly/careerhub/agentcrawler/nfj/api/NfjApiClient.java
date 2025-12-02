package com.milosz.podsiadly.careerhub.agentcrawler.nfj.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
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

    private final RestTemplate restTemplate;

    public Set<String> fetchAllJobUrls(String categorySlug) {
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
                if (p.getUrl() != null && !p.getUrl().isBlank()) {
                    String abs = "https://nofluffjobs.com/pl/job/" + p.getUrl();
                    urls.add(abs);
                }
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

        ResponseEntity<NfjSearchResponse> resp = restTemplate.exchange(
                uri,
                HttpMethod.POST,
                entity,
                NfjSearchResponse.class
        );

        if (!resp.getStatusCode().is2xxSuccessful()) {
            log.warn("[nfj-api] non-200 status={} for pageTo={} (category={})",
                    resp.getStatusCode(), pageTo, categorySlug);
            return null;
        }

        return resp.getBody();
    }
}
