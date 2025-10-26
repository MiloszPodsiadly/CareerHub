package com.milosz.podsiadly.backend.domain.favorite.dto;

public record FavoriteStatusDto(
        boolean favorited,
        long count
) {}
