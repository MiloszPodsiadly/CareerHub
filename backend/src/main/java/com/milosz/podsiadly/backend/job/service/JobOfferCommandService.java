package com.milosz.podsiadly.backend.job.service;

import com.milosz.podsiadly.backend.domain.loginandregister.User;
import com.milosz.podsiadly.backend.domain.myapplication.JobApplicationRepository;
import com.milosz.podsiadly.backend.job.domain.*;
import com.milosz.podsiadly.backend.job.dto.*;
import com.milosz.podsiadly.backend.job.mapper.JobOfferMapper;
import com.milosz.podsiadly.backend.job.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
@Slf4j
@Service
@RequiredArgsConstructor
public class JobOfferCommandService {

    private final JobOfferRepository offers;
    private final CompanyRepository companies;
    private final CityRepository cities;
    private final JobOfferOwnerRepository owners;
    private final PasswordEncoder passwordEncoder;
    private final OfferArchiveService archiveService;
    private final JobApplicationRepository applications;

    private static final int PUBLISH_DAYS = 14;

    private static String generatedExternalId(String source) {
        String s = (source == null || source.isBlank()) ? "platform" : source.trim().toLowerCase();
        return s + "-" + UUID.randomUUID();
    }

    @Transactional
    public JobOfferDetailDto create(User owner, JobOfferCreateRequest req, String platformBaseUrl) {
        String source = Optional.ofNullable(req.source()).filter(s -> !s.isBlank()).orElse("platform");
        String externalId = Optional.ofNullable(req.externalId()).filter(s -> !s.isBlank())
                .orElseGet(() -> source.toLowerCase() + "-" + UUID.randomUUID());

        Company company = upsertCompany(req.companyName());
        City city       = upsertCity(req.cityName());

        JobLevel level            = parseEnum(req.level(), JobLevel.class);
        ContractType mainContract = parseEnum(req.contract(), ContractType.class);
        Set<ContractType> contractSet = parseContractSet(req.contracts());
        Instant published         = (req.publishedAt() != null) ? req.publishedAt() : Instant.now();

        String base = platformBaseUrl.endsWith("/") ? platformBaseUrl : platformBaseUrl + "/";
        String tempUrl = base + "jobexaclyoffer?ext=" + externalId;

        JobOffer e = JobOffer.builder()
                .source(source)
                .externalId(externalId)
                .url(tempUrl)
                .applyUrl( (req.url() == null || req.url().isBlank()) ? null : req.url().trim() )
                .title(Objects.requireNonNullElse(req.title(), "Untitled"))
                .description(req.description())
                .company(company)
                .city(city)
                .remote(Boolean.TRUE.equals(req.remote()))
                .level(level)
                .contract(mainContract)
                .salaryMin(req.salaryMin())
                .salaryMax(req.salaryMax())
                .currency(req.currency())
                .publishedAt(published)
                .active(req.active() == null ? true : req.active())
                .lastSeenAt(Instant.now())
                .build();

        e.setContracts(contractSet);
        e.setTechTags(req.techTags());
        JobOfferMapper.applySkills(e, req.techStack());

        e = offers.save(e);
        e.setUrl(base + "jobexaclyoffer?id=" + e.getId());
        e = offers.save(e);

        JobOfferOwner rel = owners.save(JobOfferOwner.builder()
                .jobOffer(e)
                .user(owner)
                .createdAt(Instant.now())
                .build());

        e.setOwner(rel);
        offers.save(e);

        return JobOfferMapper.toDetailDto(e);
    }


    @Transactional
    public JobOfferDetailDto updateOwned(User currentUser, Long offerId, JobOfferUpdateRequest req) {
        requirePassword(currentUser, req.password());

        JobOfferOwner owner = owners.findByJobOffer_Id(offerId)
                .orElseThrow(() -> new IllegalArgumentException("Offer not owned by any user."));
        if (!owner.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalStateException("You are not the owner of this offer.");
        }

        JobOffer e = owner.getJobOffer();

        if (req.title() != null) e.setTitle(emptyToNull(req.title()));
        if (req.description() != null) e.setDescription(emptyToNull(req.description()));
        if (req.companyName() != null) e.setCompany(upsertCompany(req.companyName()));
        if (req.cityName() != null) e.setCity(upsertCity(req.cityName()));
        if (req.remote() != null) e.setRemote(req.remote());
        if (req.level() != null) e.setLevel(parseEnum(req.level(), JobLevel.class));
        if (req.contract() != null) e.setContract(parseEnum(req.contract(), ContractType.class));
        if (req.salaryMin() != null) e.setSalaryMin(req.salaryMin());
        if (req.salaryMax() != null) e.setSalaryMax(req.salaryMax());
        if (req.currency() != null) e.setCurrency(emptyToNull(req.currency()));
        if (req.publishedAt() != null) e.setPublishedAt(req.publishedAt());
        if (req.active() != null) e.setActive(req.active());

        if (req.contracts() != null) {
            e.setContracts(parseContractSet(req.contracts()));
        }
        if (req.techTags() != null) {
            e.setTechTags(req.techTags());
        }
        if (req.techStack() != null) {
            JobOfferMapper.applySkills(e, req.techStack());
        }

        e.setLastSeenAt(Instant.now());
        offers.save(e);

        return JobOfferMapper.toDetailDto(e);
    }

    @Transactional
    public void deleteOwned(User currentUser, Long offerId, String plainPassword) {
        log.info("[job.delete] userId={} offerId={} start", currentUser.getId(), offerId);

        requirePassword(currentUser, plainPassword);

        var owner = owners.findByJobOffer_Id(offerId)
                .orElseThrow(() -> new IllegalArgumentException("Offer not owned by any user."));
        if (!owner.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalStateException("You are not the owner of this offer.");
        }

        var offer = owner.getJobOffer();

        archiveService.archiveSingle(offer, ArchiveReason.MANUAL);

        int detached = applications.detachOfferById(offerId);
        log.info("[job.delete] applications detached (offer set null) = {} for offerId={}", detached, offerId);

        offer.setOwner(null);
        offers.saveAndFlush(offer);

        int before = owners.countByJobOffer_Id(offerId);
        owners.deleteByJobOffer_Id(offerId);
        log.info("[job.delete] owners deleted={} (before={})", before, before);

        offers.delete(offer);
        log.info("[job.delete] offer deleted ok (offerId={})", offerId);
    }

    @Transactional
    public int deactivateExpired() {
        Instant cutoff = Instant.now().minus(PUBLISH_DAYS, ChronoUnit.DAYS);
        List<JobOffer> candidates = offers.findAll((r, q, cb) ->
                cb.and(cb.isTrue(r.get("active")),
                        cb.lessThanOrEqualTo(r.get("publishedAt"), cutoff)));
        candidates.forEach(o -> o.setActive(false));
        offers.saveAll(candidates);
        return candidates.size();
    }


    private void requirePassword(User user, String plain) {
        if (plain == null || !passwordEncoder.matches(plain, user.getPassword())) {
            throw new IllegalArgumentException("Invalid password.");
        }
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

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static <E extends Enum<E>> E parseEnum(String name, Class<E> type) {
        if (name == null || name.isBlank()) return null;
        return Enum.valueOf(type, name);
    }

    private static Set<ContractType> parseContractSet(List<String> names) {
        Set<ContractType> out = new HashSet<>();
        if (names != null) {
            for (String c : names) {
                if (c != null && !c.isBlank()) out.add(ContractType.valueOf(c));
            }
        }
        return out;
    }
}
