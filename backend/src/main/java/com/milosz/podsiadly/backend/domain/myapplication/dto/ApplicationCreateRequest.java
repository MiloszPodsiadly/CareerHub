package com.milosz.podsiadly.backend.domain.myapplication.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApplicationCreateRequest(
        @NotNull(message = "Offer id is required")
        Long offerId,
        @Size(max = 2000, message = "Note must be at most 2000 characters")
        String note
) {}
