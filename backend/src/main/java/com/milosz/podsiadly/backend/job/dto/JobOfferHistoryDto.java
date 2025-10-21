package com.milosz.podsiadly.backend.job.dto;

import java.time.Instant;
import java.util.List;

public record JobOfferHistoryDto(
        Long id,
        String source,
        String externalId,
        String url,
        String title,
        String companyName,
        String cityName,
        Boolean remote,
        String level,
        String contract,
        List<String> contracts,
        Integer salaryMin,
        Integer salaryMax,
        String currency,
        Instant publishedAt,
        String reason,
        Instant archivedAt,
        Instant deactivatedAt,
        String snapshotJson
) {}
