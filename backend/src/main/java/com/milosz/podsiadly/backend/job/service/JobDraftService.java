package com.milosz.podsiadly.backend.job.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.milosz.podsiadly.backend.domain.loginandregister.User;
import com.milosz.podsiadly.backend.job.domain.JobDraft;
import com.milosz.podsiadly.backend.job.dto.*;
import com.milosz.podsiadly.backend.job.repository.JobDraftRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobDraftService {

    private final JobDraftRepository repo;
    private final JobOfferCommandService commands;
    private final ObjectMapper om;

    private static JobDraftDto toDto(JobDraft d) {
        return new JobDraftDto(d.getId(), d.getTitle(), d.getCompanyName(), d.getCityName(),
                d.getPayloadJson(), d.getCreatedAt(), d.getUpdatedAt(), d.getPublished());
    }

    @Transactional
    public JobDraftDto createEmpty(User owner) {
        JobDraft d = JobDraft.builder()
                .owner(owner).payloadJson("{}").published(false).build();
        return toDto(repo.save(d));
    }

    @Transactional
    public JobDraftDto upsert(User owner, Long id, JobDraftUpsertRequest req) {
        JobDraft d = repo.findById(id).orElseThrow();
        if (!d.getOwner().getId().equals(owner.getId())) throw new IllegalStateException("Forbidden");
        if (req.title() != null) d.setTitle(req.title());
        if (req.companyName() != null) d.setCompanyName(req.companyName());
        if (req.cityName() != null) d.setCityName(req.cityName());
        if (req.payloadJson() != null) d.setPayloadJson(req.payloadJson());
        return toDto(d);
    }

    @Transactional
    public void delete(User owner, Long id) {
        JobDraft d = repo.findById(id).orElseThrow();
        if (!d.getOwner().getId().equals(owner.getId())) throw new IllegalStateException("Forbidden");
        repo.delete(d);
    }

    @Transactional
    public JobOfferDetailDto publish(User owner, Long id, String platformBaseUrl) {
        JobDraft d = repo.findById(id).orElseThrow();
        if (!d.getOwner().getId().equals(owner.getId())) throw new IllegalStateException("Forbidden");
        try {
            JobOfferCreateRequest req = om.readValue(d.getPayloadJson(), JobOfferCreateRequest.class);
            var created = commands.create(owner, req, platformBaseUrl);
            d.setPublished(true);
            repo.save(d);
            return created;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid draft payload", e);
        }
    }

    @Transactional
    public List<JobDraftDto> mine(User owner) {
        return repo.findAllByOwner_IdOrderByUpdatedAtDesc(owner.getId())
                .stream().map(JobDraftService::toDto).toList();
    }

    @Transactional
    public JobDraftDto latest(User owner) {
        return repo.findTop1ByOwner_IdOrderByUpdatedAtDesc(owner.getId())
                .map(JobDraftService::toDto).orElse(null);
    }
}
