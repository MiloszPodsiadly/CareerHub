package com.milosz.podsiadly.backend.job.dto;

import com.milosz.podsiadly.backend.job.domain.SkillSource;

public record JobOfferSkillDto(
        String name,
        String levelLabel,
        Integer levelValue,
        SkillSource source
) {}
