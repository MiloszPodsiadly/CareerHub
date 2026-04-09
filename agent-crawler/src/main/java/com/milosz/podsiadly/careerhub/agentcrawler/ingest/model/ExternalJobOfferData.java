package com.milosz.podsiadly.careerhub.agentcrawler.ingest.model;

import com.milosz.podsiadly.careerhub.agentcrawler.job.domain.ContractType;
import com.milosz.podsiadly.careerhub.agentcrawler.job.domain.JobLevel;
import com.milosz.podsiadly.careerhub.agentcrawler.job.domain.SalaryPeriod;
import com.milosz.podsiadly.careerhub.agentcrawler.job.dto.JobOfferSkillDto;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record ExternalJobOfferData(
        String title,
        String description,
        String companyName,
        String cityName,
        Boolean remote,
        JobLevel level,
        ContractType mainContract,
        Set<ContractType> contracts,
        Integer salaryMin,
        Integer salaryMax,
        String currency,
        SalaryPeriod salaryPeriod,
        String detailsUrl,
        String applyUrl,
        List<String> techTags,
        List<JobOfferSkillDto> techStack,
        Instant publishedAt,
        Boolean active
) {
}
