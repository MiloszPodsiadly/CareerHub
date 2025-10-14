package com.milosz.podsiadly.backend.events.dto;

import com.milosz.podsiadly.backend.events.domain.EventType;
import java.time.Instant;
import java.util.List;

public record EventListDto(
        Long id,
        String source,
        String externalId,
        String url,
        String title,
        String country,
        String city,
        Boolean online,
        EventType type,
        Instant startAt,
        Instant endAt,
        List<String> tags
) {}
