package com.milosz.podsiadly.backend.domain.favorite.dto;

import java.util.List;

public record FavoritePageDto<T>(
        List<T> items,
        long total
) {}