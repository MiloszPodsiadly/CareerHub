package com.milosz.podsiadly.careerhub.agentcrawler.job.dto;

import com.milosz.podsiadly.careerhub.agentcrawler.job.domain.SkillSource;

public record JobOfferSkillDto(
        String name,
        String levelLabel,
        Integer levelValue,
        SkillSource source
) {
}
