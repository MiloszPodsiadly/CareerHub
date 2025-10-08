package com.milosz.podsiadly.backend.job.dto;

import java.time.Instant;
import java.util.List;

public record JobOfferListDto(
        Long id,
        String title,
        String company,
        String city,
        Boolean remote,
        String level,
        Integer salaryMin,
        Integer salaryMax,
        String currency,
        List<String> techTags,
        Instant publishedAt
) {}