package com.milosz.podsiadly.backend.ingest.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record ExternalOfferMessage(
        @NotBlank(message = "Source is required")
        String source,
        @NotBlank(message = "External id is required")
        String externalId,
        String url,
        String title,
        String description,
        String companyName,
        String cityName,
        Boolean remote,
        String level,
        String mainContract,
        Set<String> contracts,
        Integer salaryMin,
        Integer salaryMax,
        String currency,
        String salaryPeriod,
        String applyUrl,
        List<String> techTags,
        Instant publishedAt,
        Boolean active
) {
    @AssertTrue(message = "Url or applyUrl is required")
    public boolean hasAddress() {
        return notBlank(url) || notBlank(applyUrl);
    }

    @AssertTrue(message = "Active offer must have a title")
    public boolean hasTitleWhenActive() {
        return Boolean.FALSE.equals(active) || notBlank(title);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
