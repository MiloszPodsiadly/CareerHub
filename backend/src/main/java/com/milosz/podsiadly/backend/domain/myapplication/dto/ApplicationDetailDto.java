package com.milosz.podsiadly.backend.domain.myapplication.dto;

import java.time.Instant;

public record ApplicationDetailDto(
        Long id,
        Long offerId,
        String offerTitle,
        String offerOwnerName,
        String applicantUsername,
        String applicantEmail,
        String note,
        String status,
        String applyUrl,
        String cvDownloadUrl,
        Instant createdAt
) {}
