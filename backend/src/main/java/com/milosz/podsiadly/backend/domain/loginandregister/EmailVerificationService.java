package com.milosz.podsiadly.backend.domain.loginandregister;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokens;
    private final MailService mailService;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.email-verify.exp-hours:24}")
    private long expHours;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public void sendVerificationLink(User user) {
        if (user.isEmailVerified()) return;

        String tokenValue = generateToken();

        EmailVerificationToken token = EmailVerificationToken.builder()
                .token(tokenValue)
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(expHours))
                .used(false)
                .createdAt(LocalDateTime.now())
                .build();

        tokens.save(token);

        String link = frontendUrl + "/auth/verify?token=" + tokenValue;
        mailService.sendEmailVerification(user.getEmail(), link);
    }

    @Transactional
    public void verify(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid token");
        }

        EmailVerificationToken token = tokens.findByToken(tokenValue)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid token"));

        if (token.isUsed() || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token expired or already used");
        }

        User user = token.getUser();
        user.setEmailVerified(true);

        token.setUsed(true);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
    @Transactional
    public void resend(String email, LoginRepository users) {
        if (email == null || email.isBlank()) return;

        var userOpt = users.findByEmail(email.trim().toLowerCase());
        if (userOpt.isEmpty()) return;

        User user = userOpt.get();
        if (user.isEmailVerified()) return;
        tokens.invalidateAllActiveForUser(user.getId(), LocalDateTime.now());

        sendVerificationLink(user);
    }

}
