package com.milosz.podsiadly.backend.job.service.ingest;

import com.milosz.podsiadly.backend.job.domain.*;
import com.milosz.podsiadly.backend.job.mapper.JobOfferMapper;
import com.milosz.podsiadly.backend.job.repository.CityRepository;
import com.milosz.podsiadly.backend.job.repository.CompanyRepository;
import com.milosz.podsiadly.backend.job.repository.JobOfferRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ExternalJobOfferIngestService {

    private final JobOfferRepository offers;
    private final CompanyRepository companies;
    private final CityRepository cities;

    @Transactional
    public JobOffer ingest(JobSource source, String externalId, ExternalJobOfferData data) {

        JobOffer offer = offers.findBySourceAndExternalId(source, externalId)
                .orElseGet(() -> JobOffer.builder()
                        .source(source)
                        .externalId(externalId)
                        .build());

        Company company = upsertCompany(data.companyName());
        City city       = upsertCity(data.cityName());

        offer.setTitle(data.title());
        offer.setDescription(data.description());
        offer.setCompany(company);
        offer.setCity(city);
        offer.setRemote(data.remote());
        offer.setLevel(data.level());
        offer.setContract(data.mainContract());
        offer.setSalaryMin(data.salaryMin());
        offer.setSalaryMax(data.salaryMax());
        offer.setCurrency(data.currency());

        offer.setUrl(data.detailsUrl());
        offer.setApplyUrl(data.applyUrl() != null ? data.applyUrl() : data.detailsUrl());

        offer.setContracts(data.contracts());
        offer.setTechTags(data.techTags());
        JobOfferMapper.applySkills(offer, data.techStack());

        offer.setPublishedAt(data.publishedAt() != null ? data.publishedAt() : Instant.now());
        offer.setActive(data.active() == null ? true : data.active());
        offer.setLastSeenAt(Instant.now());

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
