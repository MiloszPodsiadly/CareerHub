package com.milosz.podsiadly.backend.domain.loginandregister;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    @Query("""
    select t
      from PasswordResetToken t
      join fetch t.user
     where t.token = :token
""")
    Optional<PasswordResetToken> findByToken(@Param("token") String token);

    Optional<PasswordResetToken> findTopByUserIdOrderByCreatedAtDesc(String userId);

    long countByUserIdAndCreatedAtAfter(String userId, LocalDateTime after);

    @Modifying
    @Query("""
    update PasswordResetToken t
       set t.used = true
     where t.user.id = :userId
       and t.used = false
       and t.expiresAt > :now
""")
    int invalidateAllActiveForUser(@Param("userId") String userId, @Param("now") LocalDateTime now);
}
