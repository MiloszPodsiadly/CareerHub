package com.milosz.podsiadly.backend.domain.myapplication.dto;

public record ApplicationCreateRequest(
        Long offerId,
        String note
) {}
