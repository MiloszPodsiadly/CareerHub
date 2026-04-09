package com.milosz.podsiadly.backend.job.dto;

import com.milosz.podsiadly.backend.job.domain.SkillSource;
import jakarta.validation.constraints.NotBlank;

public record JobOfferSkillDto(
        @NotBlank(message = "Skill name is required")
        String name,
        String levelLabel,
        Integer levelValue,
        SkillSource source
) {}
