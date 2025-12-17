package com.milosz.podsiadly.backend.domain.loginandregister;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByToken(String token);
    @Modifying
    @Query("""
    update EmailVerificationToken t
       set t.used = true
     where t.user.id = :userId
       and t.used = false
       and t.expiresAt > :now
""")
    int invalidateAllActiveForUser(@Param("userId") String userId, @Param("now") LocalDateTime now);

}
