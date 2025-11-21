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

        String srcName = (p.source() != null && !p.source().isBlank())
                ? p.source()
                : JobSource.JUSTJOIN.name();
        JobSource src = JobSource.valueOf(srcName);

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

        JobOffer e = offers.findBySourceAndExternalId(src, p.externalId())
                .orElseGet(() -> {
                    JobOffer ne = new JobOffer();
                    ne.setSource(src);
                    ne.setTechStack(new ArrayList<>());
                    return ne;
                });

        e.setSource(src);
        e.setExternalId(p.externalId());
        e.setUrl(p.url());
        e.setTitle(p.title());
        e.setDescription(p.description());
        e.setCompany(company);
        e.setCity(city);
        e.setRemote(p.remote());
        e.setLevel(p.level());
        e.setContract(mapContract(p.contract()));
        e.setContracts(mapContracts(p.contracts()));

        e.setSalaryMin(p.min());
        e.setSalaryMax(p.max());
        e.setCurrency(p.currency());

        List<String> tags = (p.techTags() != null && !p.techTags().isEmpty())
                ? p.techTags()
                : (p.techStack() != null
                ? p.techStack().stream().map(JustJoinParser.ParsedSkill::name).distinct().limit(24).toList()
                : Collections.emptyList());
        e.setTechTags(tags);

        e.setPublishedAt(p.publishedAt() != null ? p.publishedAt() : Instant.now());
        e.setLastSeenAt(Instant.now());
        e.setActive(true);

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

    private static ContractType mapContract(String s) {
        if (s == null) return null;
        return switch (s.trim().toUpperCase(Locale.ROOT)) {
            case "B2B" -> ContractType.B2B;
            case "UOP" -> ContractType.UOP;
            case "UZ"  -> ContractType.UZ;
            case "UOD" -> ContractType.UOD;
            default    -> null;
        };
    }

    private static Set<ContractType> mapContracts(Set<String> src) {
        if (src == null || src.isEmpty()) return Set.of();
        EnumSet<ContractType> out = EnumSet.noneOf(ContractType.class);
        for (String s : src) {
            ContractType ct = mapContract(s);
            if (ct != null) out.add(ct);
        }
        return out;
    }

    private SkillSource toSourceEnum(String src) {
        if (src == null) return SkillSource.STACK;
        return switch (src.toUpperCase(Locale.ROOT)) {
            case "REQUIRED"     -> SkillSource.REQUIRED;
            case "NICE_TO_HAVE" -> SkillSource.NICE_TO_HAVE;
            case "LD"           -> SkillSource.LD;
            case "STACK"        -> SkillSource.STACK;
            default             -> SkillSource.STACK;
        };
    }
}
