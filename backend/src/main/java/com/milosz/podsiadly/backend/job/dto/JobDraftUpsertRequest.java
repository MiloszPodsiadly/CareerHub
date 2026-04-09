package com.milosz.podsiadly.backend.job.dto;

import jakarta.validation.constraints.AssertTrue;

public record JobDraftUpsertRequest(
        String title,
        String companyName,
        String cityName,
        String payloadJson
) {
    @AssertTrue(message = "At least one field must be provided")
    public boolean hasAnyField() {
        return title != null || companyName != null || cityName != null || payloadJson != null;
    }
}
