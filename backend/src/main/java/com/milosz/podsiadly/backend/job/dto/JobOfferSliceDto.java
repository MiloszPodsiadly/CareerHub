package com.milosz.podsiadly.backend.job.dto;

import java.util.List;

public record JobOfferSliceDto(
        List<JobOfferListDto> content,
        int page,
        int size,
        boolean hasNext
) {}
