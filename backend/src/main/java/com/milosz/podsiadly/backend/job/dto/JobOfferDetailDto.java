package com.milosz.podsiadly.backend.job.dto;

import java.time.Instant;
import java.util.List;

public record JobOfferDetailDto(
        Long id,
        String source,
        String externalId,
        String url,
        String title,
        String description,
        String companyName,
        String cityName,
        Boolean remote,
        String level,
        String contract,
        List<String> contracts,
        Integer salaryMin,
        Integer salaryMax,
        String currency,
        List<String> techTags,
        List<JobOfferSkillDto> techStack,
        Instant publishedAt,
        Boolean active
) {}
