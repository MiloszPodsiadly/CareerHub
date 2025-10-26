package com.milosz.podsiadly.backend.domain.favorite.dto;

import com.milosz.podsiadly.backend.domain.favorite.FavoriteType;
import java.time.Instant;

public record FavoriteDto(
        Long id,
        String userId,
        FavoriteType type,
        Long targetId,
        Instant createdAt
) {}
