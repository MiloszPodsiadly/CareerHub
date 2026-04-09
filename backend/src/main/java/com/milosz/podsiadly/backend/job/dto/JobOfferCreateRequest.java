package com.milosz.podsiadly.backend.job.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public record JobOfferCreateRequest(
        String source,
        String externalId,
        String url,
        @NotBlank(message = "Title is required")
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
        List<@Valid JobOfferSkillDto> techStack,
        Instant publishedAt,
        Boolean active
) {
    @AssertTrue(message = "Salary min cannot be greater than salary max")
    public boolean isSalaryRangeValid() {
        return salaryMin == null || salaryMax == null || salaryMin <= salaryMax;
    }
}
