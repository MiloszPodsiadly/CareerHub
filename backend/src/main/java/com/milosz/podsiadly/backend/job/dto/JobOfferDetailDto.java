package com.milosz.podsiadly.backend.job.dto;

import com.milosz.podsiadly.backend.job.dto.JobOfferSkillDto;

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
        Integer salaryMin,
        Integer salaryMax,
        String currency,
        List<String> techTags,
        List<JobOfferSkillDto> techStack,   // <— NOWE
        Instant publishedAt,
        Boolean active
) {}
