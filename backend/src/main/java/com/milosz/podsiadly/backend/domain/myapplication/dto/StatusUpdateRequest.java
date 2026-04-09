package com.milosz.podsiadly.backend.domain.myapplication.dto;

import jakarta.validation.constraints.NotBlank;

public record StatusUpdateRequest(
        @NotBlank(message = "Status is required")
        String status
) {}
