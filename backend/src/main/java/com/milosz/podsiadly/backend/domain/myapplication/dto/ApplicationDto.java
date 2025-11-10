package com.milosz.podsiadly.backend.domain.myapplication.dto;

import java.time.Instant;

public record ApplicationDto(
        Long id,
        Long offerId,
        String offerTitle,
        String companyName,
        String cityName,
        String status,
        String cvDownloadUrl,
        Instant createdAt
) {}
