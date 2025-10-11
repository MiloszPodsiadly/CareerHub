package com.milosz.podsiadly.backend.job.dto;

import java.time.Instant;

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
        Integer salaryMin,
        Integer salaryMax,
        String currency,
        Instant publishedAt,
        String reason,
        Instant archivedAt,
        Instant deactivatedAt,
        String snapshotJson
) {}