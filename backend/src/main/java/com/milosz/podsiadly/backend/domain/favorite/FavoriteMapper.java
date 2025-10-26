package com.milosz.podsiadly.backend.domain.favorite;

import com.milosz.podsiadly.backend.domain.favorite.dto.FavoriteDto;

final class FavoriteMapper {
    static FavoriteDto toDto(Favorite f) {
        return new FavoriteDto(
                f.getId(),
                f.getUserId(),
                f.getType(),
                f.getTargetId(),
                f.getCreatedAt()
        );
    }

    private FavoriteMapper() {}
}
