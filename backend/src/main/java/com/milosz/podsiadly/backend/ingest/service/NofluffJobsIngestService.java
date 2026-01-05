package com.milosz.podsiadly.backend.ingest.service;

import com.milosz.podsiadly.backend.ingest.dto.NofluffJobDto;
import com.milosz.podsiadly.backend.ingest.parser.NfjHtmlParser;
import com.milosz.podsiadly.backend.job.domain.JobSource;
import com.milosz.podsiadly.backend.job.domain.SalaryPeriod;
import com.milosz.podsiadly.backend.job.service.ingest.ExternalJobOfferData;
import com.milosz.podsiadly.backend.job.service.ingest.ExternalJobOfferIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NofluffJobsIngestService {

    private final ExternalJobOfferIngestService ingestService;
    private final RestTemplate restTemplate;
    private final NfjHtmlParser htmlParser;

    @Transactional
    public void importSingle(NofluffJobDto dto) {

        Instant published = (dto.publishedAt() != null) ? dto.publishedAt() : Instant.now();
        SalaryPeriod period = (dto.salaryPeriod() != null) ? dto.salaryPeriod() : SalaryPeriod.MONTH;

        Boolean active = resolveActiveFromHtml(dto);

        ExternalJobOfferData data = new ExternalJobOfferData(
                dto.title(),
                dto.description(),
                dto.companyName(),
                dto.cityName(),
                dto.remote(),
                dto.level(),
                dto.mainContract(),
                dto.contracts(),
                dto.salaryMin(),
                dto.salaryMax(),
                dto.currency(),
                period,
                dto.detailsUrl(),
                dto.applyUrl(),
                dto.techTags(),
                dto.techStack() != null ? dto.techStack() : List.of(),
                published,
                active
        );

        ingestService.ingest(JobSource.NOFLUFFJOBS, dto.externalId(), data);
    }

    private Boolean resolveActiveFromHtml(NofluffJobDto dto) {
        if (dto.active() != null) return dto.active();

        String url = dto.detailsUrl();
        if (url == null || url.isBlank()) return true;

        try {
            String html = restTemplate.getForObject(url, String.class);
            if (html == null || html.isBlank()) return true;

            boolean expired = htmlParser.isExpired(html);
            if (expired) {
                log.info("[nfj] detected expired offer externalId={} url={}", dto.externalId(), url);
            }
            return !expired;
        } catch (RestClientException ex) {
            log.warn("[nfj] html fetch failed externalId={} url={} err={}", dto.externalId(), url, ex.toString());
            return true;
        }
    }
}
