package com.milosz.podsiadly.backend.domain.loginandregister;

import com.milosz.podsiadly.backend.domain.loginandregister.dto.ForgotPasswordRequest;
import com.milosz.podsiadly.backend.domain.loginandregister.dto.ResetPasswordRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final LoginRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.password-reset.exp-hours:1}")
    private long tokenExpHours;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public void sendResetLink(ForgotPasswordRequest req) {
        if (req.email() == null || req.email().isBlank()) {
            return;
        }

        String email = req.email().trim().toLowerCase();
        var userOpt = users.findByEmail(email);
        if (userOpt.isEmpty()) {
            return;
        }

        var user = userOpt.get();

        String tokenValue = generateToken();

        PasswordResetToken token = PasswordResetToken.builder()
                .token(tokenValue)
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(tokenExpHours))
                .used(false)
                .createdAt(LocalDateTime.now())
                .build();

        tokens.save(token);

        String link = frontendUrl + "/auth/reset-password?token=" + tokenValue;
        mailService.sendPasswordResetEmail(user.getEmail(), link);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        if (req.token() == null || req.token().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid reset token");
        }
        if (req.newPassword() == null || req.newPassword().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Password is required");
        }

        PasswordResetToken token = tokens.findByToken(req.token())
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid reset token"));

        if (token.isUsed() || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(BAD_REQUEST, "Reset token expired or already used");
        }

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(req.newPassword()));

        token.setUsed(true);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
}
