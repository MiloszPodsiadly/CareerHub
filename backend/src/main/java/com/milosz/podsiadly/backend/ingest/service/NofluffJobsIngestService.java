    package com.milosz.podsiadly.backend.ingest.service;

    import com.milosz.podsiadly.backend.ingest.dto.NofluffJobDto;
    import com.milosz.podsiadly.backend.job.domain.JobSource;
    import com.milosz.podsiadly.backend.job.service.ingest.ExternalJobOfferData;
    import com.milosz.podsiadly.backend.job.service.ingest.ExternalJobOfferIngestService;
    import lombok.RequiredArgsConstructor;
    import org.springframework.stereotype.Service;
    import org.springframework.transaction.annotation.Transactional;

    import java.time.Instant;
    import java.util.List;

    @Service
    @RequiredArgsConstructor
    public class NofluffJobsIngestService {

        private final ExternalJobOfferIngestService ingestService;

        @Transactional
        public void importSingle(NofluffJobDto dto) {
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
                    dto.detailsUrl(),
                    dto.applyUrl(),
                    dto.techTags(),
                    dto.techStack() != null ? dto.techStack() : List.of(),
                    dto.publishedAt() != null ? dto.publishedAt() : Instant.now(),
                    true
            );

            ingestService.ingest(JobSource.NOFLUFFJOBS, dto.externalId(), data);
        }
    }
