package com.milosz.podsiadly.backend.job.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public record JobOfferUpdateRequest(
        @NotBlank(message = "Password is required")
        String password,
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
    @AssertTrue(message = "At least one field to update must be provided")
    public boolean hasUpdates() {
        return title != null
                || description != null
                || companyName != null
                || cityName != null
                || remote != null
                || level != null
                || contract != null
                || contracts != null
                || salaryMin != null
                || salaryMax != null
                || currency != null
                || techTags != null
                || techStack != null
                || publishedAt != null
                || active != null;
    }

    @AssertTrue(message = "Salary min cannot be greater than salary max")
    public boolean isSalaryRangeValid() {
        return salaryMin == null || salaryMax == null || salaryMin <= salaryMax;
    }
}
