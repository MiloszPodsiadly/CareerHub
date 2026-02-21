package com.milosz.podsiadly.backend.domain.loginandregister.mail;

import com.milosz.podsiadly.backend.domain.loginandregister.EmailVerificationTokenRepository;
import com.milosz.podsiadly.backend.domain.loginandregister.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MailRateLimiter {

    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final MailRateLimitProperties props;

    public boolean allowVerification(String userId) {
        var now = LocalDateTime.now();
        var last = verificationTokens.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        if (last != null && last.getCreatedAt().isAfter(now.minus(props.getVerification().getMinInterval()))) {
            return false;
        }

        long count = verificationTokens.countByUserIdAndCreatedAtAfter(userId, now.minusDays(1));
        return count < props.getVerification().getMaxPerDay();
    }

    public boolean allowReset(String userId) {
        var now = LocalDateTime.now();
        var last = resetTokens.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        if (last != null && last.getCreatedAt().isAfter(now.minus(props.getReset().getMinInterval()))) {
            return false;
        }

        long count = resetTokens.countByUserIdAndCreatedAtAfter(userId, now.minusDays(1));
        return count < props.getReset().getMaxPerDay();
    }
}
