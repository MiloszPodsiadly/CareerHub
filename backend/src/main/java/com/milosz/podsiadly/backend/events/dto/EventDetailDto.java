package com.milosz.podsiadly.backend.events.dto;

import com.milosz.podsiadly.backend.events.domain.EventType;
import java.time.Instant;
import java.util.List;

public record EventDetailDto(
        Long id,
        String source,
        String externalId,
        String url,
        String title,
        String description,
        String country,
        String city,
        Boolean online,
        EventType type,
        Instant startAt,
        Instant endAt,
        String venue,
        Double latitude,
        Double longitude,
        List<String> tags
) {}
