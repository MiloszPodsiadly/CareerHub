package com.milosz.podsiadly.backend.job.dto;

import java.time.Instant;

public record JobDraftDto(
        Long id,
        String title,
        String companyName,
        String cityName,
        String payloadJson,
        Instant createdAt,
        Instant updatedAt,
        Boolean published
) {}
