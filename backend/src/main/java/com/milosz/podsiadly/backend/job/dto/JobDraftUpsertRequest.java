package com.milosz.podsiadly.backend.job.dto;

public record JobDraftUpsertRequest(
        String title,
        String companyName,
        String cityName,
        String payloadJson
) {}
