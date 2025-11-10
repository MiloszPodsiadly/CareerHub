package com.milosz.podsiadly.backend.job.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.milosz.podsiadly.backend.job.domain.JobOffer;
import com.milosz.podsiadly.backend.job.domain.ArchiveReason;
import com.milosz.podsiadly.backend.job.domain.JobOfferHistory;
import com.milosz.podsiadly.backend.job.dto.JobOfferDetailDto;
import com.milosz.podsiadly.backend.job.dto.JobOfferHistoryDto;
import com.milosz.podsiadly.backend.job.dto.JobOfferSkillDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JobOfferHistoryMapper {

    private final ObjectMapper om;

    public JobOfferHistory toHistory(JobOffer o, ArchiveReason reason) {
        List<String> contracts = (o.getContracts() != null)
                ? o.getContracts().stream().map(Enum::name).distinct().toList()
                : (o.getContract() != null ? List.of(o.getContract().name()) : List.of());

        return JobOfferHistory.builder()
                .source(o.getSource())
                .externalId(o.getExternalId())
                .url(o.getUrl() != null ? o.getUrl() : "")
                .title(o.getTitle() != null ? o.getTitle() : "(no title)")
                .companyName(o.getCompany() != null ? o.getCompany().getName() : null)
                .cityName(o.getCity() != null ? o.getCity().getName() : null)
                .remote(o.getRemote())
                .level(o.getLevel() != null ? o.getLevel().name() : null)
                .contract(o.getContract() != null ? o.getContract().name() : null)
                .contracts(contracts)
                .salaryMin(o.getSalaryMin())
                .salaryMax(o.getSalaryMax())
                .currency(o.getCurrency())
                .publishedAt(o.getPublishedAt())
                .reason(reason)
                .archivedAt(Instant.now())
                .deactivatedAt(o.getLastSeenAt() != null ? o.getLastSeenAt() : Instant.now())
                .snapshotJson(buildSnapshot(o, contracts))
                .build();
    }

    public JobOfferHistoryDto toDto(JobOfferHistory h) {
        return new JobOfferHistoryDto(
                h.getId(),
                h.getSource(),
                h.getExternalId(),
                h.getUrl(),
                h.getTitle(),
                h.getCompanyName(),
                h.getCityName(),
                h.getRemote(),
                h.getLevel(),
                h.getContract(),
                h.getContracts(),
                h.getSalaryMin(),
                h.getSalaryMax(),
                h.getCurrency(),
                h.getPublishedAt(),
                h.getReason() != null ? h.getReason().name() : null,
                h.getArchivedAt(),
                h.getDeactivatedAt(),
                h.getSnapshotJson()
        );
    }

    private String buildSnapshot(JobOffer o, List<String> contracts) {
        var detail = new JobOfferDetailDto(
                o.getId(),
                o.getSource(),
                o.getExternalId(),
                o.getUrl(),
                o.getApplyUrl(),
                o.getTitle(),
                o.getDescription(),
                o.getCompany() != null ? o.getCompany().getName() : null,
                o.getCity() != null ? o.getCity().getName() : null,
                o.getRemote(),
                o.getLevel() != null ? o.getLevel().name() : null,
                o.getContract() != null ? o.getContract().name() : null,
                contracts,
                o.getSalaryMin(),
                o.getSalaryMax(),
                o.getCurrency(),
                o.getTechTags(),
                o.getTechStack().stream()
                        .map(s -> new JobOfferSkillDto(
                                s.getName(),
                                s.getLevelLabel(),
                                s.getLevelValue(),
                                s.getSource()))
                        .toList(),
                o.getPublishedAt(),
                o.getActive()
        );
        try {
            return om.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            return "{\"id\":" + o.getId() + ",\"title\":\"" + escape(o.getTitle()) + "\"}";
        }
    }


    private static String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
