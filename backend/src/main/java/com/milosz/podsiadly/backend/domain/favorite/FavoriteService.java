package com.milosz.podsiadly.backend.domain.favorite;

import com.milosz.podsiadly.backend.domain.favorite.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository repo;

    @Transactional
    public void add(String userId, FavoriteType type, Long targetId) {
        if (repo.existsByUserIdAndTypeAndTargetId(userId, type, targetId)) return;
        var f = Favorite.builder()
                .userId(userId).type(type).targetId(targetId)
                .build();
        repo.save(f);
    }

    @Transactional
    public void remove(String userId, FavoriteType type, Long targetId) {
        repo.delete(userId, type, targetId);
    }

    @Transactional
    public boolean toggle(String userId, FavoriteType type, Long targetId) {
        var ex = repo.findByUserIdAndTypeAndTargetId(userId, type, targetId);
        if (ex.isPresent()) {
            repo.delete(userId, type, targetId);
            return false;
        } else {
            add(userId, type, targetId);
            return true;
        }
    }

    @Transactional(readOnly = true)
    public FavoriteStatusDto status(String maybeUserId, FavoriteType type, Long targetId) {
        long count = repo.countByTypeAndTargetId(type, targetId);
        boolean fav = false;
        if (maybeUserId != null) {
            fav = repo.existsByUserIdAndTypeAndTargetId(maybeUserId, type, targetId);
        }
        return new FavoriteStatusDto(fav, count);
    }

    @Transactional(readOnly = true)
    public FavoritePageDto<FavoriteDto> listMine(String userId, FavoriteType type, int page, int size) {
        var p = repo.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type, PageRequest.of(page, size));
        return new FavoritePageDto<>(
                p.getContent().stream().map(FavoriteMapper::toDto).collect(Collectors.toList()),
                p.getTotalElements()
        );
    }
}
