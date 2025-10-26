package com.milosz.podsiadly.backend.domain.favorite;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    Optional<Favorite> findByUserIdAndTypeAndTargetId(
            String userId, FavoriteType type, Long targetId);

    boolean existsByUserIdAndTypeAndTargetId(
            String userId, FavoriteType type, Long targetId);

    @Modifying
    @Query("delete from Favorite f where f.userId=:userId and f.type=:type and f.targetId=:targetId")
    void delete(String userId, FavoriteType type, Long targetId);

    Page<Favorite> findByUserIdAndTypeOrderByCreatedAtDesc(
            String userId, FavoriteType type, Pageable pageable);

    long countByTypeAndTargetId(FavoriteType type, Long targetId);
}