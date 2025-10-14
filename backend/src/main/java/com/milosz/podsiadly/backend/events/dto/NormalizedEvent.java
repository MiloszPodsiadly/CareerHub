package com.milosz.podsiadly.backend.events.dto;

import com.milosz.podsiadly.backend.events.domain.EventType;
import java.time.Instant;
import java.util.List;

public record NormalizedEvent(
        String source, String externalId, String url,
        String title, String description,
        String country, String region, String city, String timezone,
        Boolean online, EventType type,
        Instant startAt, Instant endAt, String status,
        String venue, Double lat, Double lon,
        List<String> tags, String rawJson
) {}

