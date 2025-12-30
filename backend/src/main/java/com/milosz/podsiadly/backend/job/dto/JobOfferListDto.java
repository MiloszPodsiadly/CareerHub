package com.milosz.podsiadly.backend.job.dto;

import java.time.Instant;
import java.util.List;

public record JobOfferListDto(
        Long id,
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
        String salaryPeriod,
        List<String> techTags,
        Instant publishedAt
) {}
