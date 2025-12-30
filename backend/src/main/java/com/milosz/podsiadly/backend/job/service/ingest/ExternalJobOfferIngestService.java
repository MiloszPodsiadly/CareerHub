package com.milosz.podsiadly.backend.job.service.ingest;

import com.milosz.podsiadly.backend.job.domain.City;
import com.milosz.podsiadly.backend.job.domain.Company;
import com.milosz.podsiadly.backend.job.domain.JobOffer;
import com.milosz.podsiadly.backend.job.domain.JobSource;
import com.milosz.podsiadly.backend.job.domain.SalaryPeriod;
import com.milosz.podsiadly.backend.job.mapper.JobOfferMapper;
import com.milosz.podsiadly.backend.job.repository.CityRepository;
import com.milosz.podsiadly.backend.job.repository.CompanyRepository;
import com.milosz.podsiadly.backend.job.repository.JobOfferRepository;
import com.milosz.podsiadly.backend.job.service.SalaryNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalJobOfferIngestService {

    private final JobOfferRepository offers;
    private final CompanyRepository companies;
    private final CityRepository cities;

    @Transactional
    public JobOffer ingest(JobSource source, String externalId, ExternalJobOfferData data) {

        Optional<JobOffer> existingOpt = offers.findBySourceAndExternalId(source, externalId);

        JobOffer offer = existingOpt.orElseGet(() -> JobOffer.builder()
                .source(source)
                .externalId(externalId)
                .build());

        Company company = upsertCompany(data.companyName());
        City city       = upsertCity(data.cityName());

        String title = data.title();
        if (title == null || title.isBlank()) {
            title = externalId;
            log.debug("[ingest] using externalId as fallback title for {}:{}", source, externalId);
        }

        offer.setTitle(title);
        offer.setDescription(data.description());
        offer.setCompany(company);
        offer.setCity(city);
        offer.setRemote(data.remote());
        offer.setLevel(data.level());
        offer.setContract(data.mainContract());
        offer.setSalaryMin(data.salaryMin());
        offer.setSalaryMax(data.salaryMax());
        offer.setCurrency(data.currency());
        SalaryPeriod period = data.salaryPeriod() != null ? data.salaryPeriod() : SalaryPeriod.MONTH;
        offer.setSalaryPeriod(period);

        SalaryNormalizer.Normalized norm = SalaryNormalizer.normalizeToMonth(
                data.salaryMin(), data.salaryMax(), period
        );
        offer.setSalaryNormMonthMin(norm.monthMin());
        offer.setSalaryNormMonthMax(norm.monthMax());
        offer.setUrl(data.detailsUrl());
        offer.setApplyUrl(data.applyUrl() != null ? data.applyUrl() : data.detailsUrl());
        offer.setContracts(data.contracts());
        offer.setTechTags(data.techTags());
        JobOfferMapper.applySkills(offer, data.techStack());
        offer.setPublishedAt(data.publishedAt() != null ? data.publishedAt() : Instant.now());

        if (data.active() != null) {
            offer.setActive(data.active());
        } else if (offer.getId() == null) {
            offer.setActive(true);
        }

        if (!Boolean.FALSE.equals(offer.getActive())) {
            offer.setLastSeenAt(Instant.now());
        }

        return offers.save(offer);
    }

    private Company upsertCompany(String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim();
        companies.insertIgnore(n);
        return companies.findFirstByNameIgnoreCaseOrderByIdAsc(n)
                .orElseGet(() -> companies.save(Company.builder().name(n).build()));
    }

    private City upsertCity(String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim();
        return cities.findFirstByNameIgnoreCaseOrderByIdAsc(n)
                .orElseGet(() -> cities.save(City.builder().name(n).build()));
    }
}
