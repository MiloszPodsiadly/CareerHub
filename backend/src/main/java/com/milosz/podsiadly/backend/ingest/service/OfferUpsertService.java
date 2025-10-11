package com.milosz.podsiadly.backend.ingest.service;

import com.milosz.podsiadly.backend.ingest.parser.JustJoinParser;
import com.milosz.podsiadly.backend.job.domain.*;
import com.milosz.podsiadly.backend.job.dto.JobOfferSkillDto;
import com.milosz.podsiadly.backend.job.mapper.JobOfferMapper;
import com.milosz.podsiadly.backend.job.repository.CityRepository;
import com.milosz.podsiadly.backend.job.repository.CompanyRepository;
import com.milosz.podsiadly.backend.job.repository.JobOfferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OfferUpsertService {

    private final CompanyRepository companies;
    private final CityRepository cities;
    private final JobOfferRepository offers;

    @Transactional
    public void upsert(JustJoinParser.ParsedOffer p) {
        Company company = null;
        if (notBlank(p.companyName())) {
            String name = normalizeName(p.companyName());
            companies.insertIgnore(name);
            company = companies.findFirstByNameIgnoreCaseOrderByIdAsc(name).orElse(null);
        }

        City city = null;
        if (notBlank(p.cityName())) {
            String name = normalizeName(p.cityName());
            city = cities.findFirstByNameIgnoreCaseOrderByIdAsc(name)
                    .orElseGet(() -> cities.save(City.builder()
                            .name(name)
                            .countryCode("PL")
                            .build()));
        }

        JobOffer e = offers.findBySourceAndExternalId(p.source(), p.externalId())
                .orElseGet(() -> {
                    JobOffer ne = new JobOffer();
                    ne.setTechStack(new ArrayList<>());
                    return ne;
                });

        e.setSource(p.source());
        e.setExternalId(p.externalId());
        e.setUrl(p.url());
        e.setTitle(p.title());
        e.setDescription(p.description());
        e.setCompany(company);
        e.setCity(city);
        e.setRemote(p.remote());
        e.setLevel(p.level());
        e.setSalaryMin(p.min());
        e.setSalaryMax(p.max());
        e.setCurrency(p.currency());
        e.setTechTags(p.techStack() != null
                ? p.techStack().stream().map(JustJoinParser.ParsedSkill::name).distinct().limit(24).toList()
                : (p.techTags() != null ? p.techTags() : Collections.emptyList()));
        e.setPublishedAt(p.publishedAt() != null ? p.publishedAt() : Instant.now());
        e.setLastSeenAt(Instant.now());
        e.setActive(true);

        if (e.getTechStack() == null) e.setTechStack(new ArrayList<>());
        else e.getTechStack().clear();

        List<JobOfferSkillDto> skills = new ArrayList<>();
        if (p.techStack() != null) {
            for (JustJoinParser.ParsedSkill s : p.techStack()) {
                skills.add(new JobOfferSkillDto(
                        s.name(), s.levelLabel(), s.levelValue(), toSourceEnum(s.source())
                ));
            }
        }
        JobOfferMapper.applySkills(e, skills);

        offers.save(e);
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
    private static String normalizeName(String s) {
        if (s == null) return null;
        return s.trim().replaceAll("\\s{2,}", " ");
    }

    private SkillSource toSourceEnum(String src) {
        if (src == null) return SkillSource.STACK;
        return switch (src.toUpperCase(Locale.ROOT)) {
            case "REQUIRED" -> SkillSource.REQUIRED;
            case "NICE_TO_HAVE" -> SkillSource.NICE_TO_HAVE;
            case "LD" -> SkillSource.LD;
            case "STACK" -> SkillSource.STACK;
            default -> SkillSource.STACK;
        };
    }
}
