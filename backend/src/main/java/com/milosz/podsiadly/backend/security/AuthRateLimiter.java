package com.milosz.podsiadly.backend.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AuthRateLimiter {
    private final Cache<String, AtomicInteger> loginAttempts;
    private final Cache<String, AtomicInteger> refreshAttempts;
    private final Cache<String, AtomicInteger> forgotPasswordAttempts;
    private final Cache<String, AtomicInteger> resendVerificationAttempts;
    private final AuthRateLimitProperties properties;

    public AuthRateLimiter(AuthRateLimitProperties properties) {
        this.properties = properties;
        this.loginAttempts = newCache(properties.getLogin().getWindow());
        this.refreshAttempts = newCache(properties.getRefresh().getWindow());
        this.forgotPasswordAttempts = newCache(properties.getForgotPassword().getWindow());
        this.resendVerificationAttempts = newCache(properties.getResendVerification().getWindow());
    }

    public void checkLogin(String clientKey, String email) {
        AuthRateLimitProperties.Limit limit = properties.getLogin();
        check(loginAttempts, "ip|" + normalize(clientKey), limit, "Too many login attempts. Try again later.");
        check(loginAttempts, "email|" + normalize(email), limit, "Too many login attempts. Try again later.");
    }

    public void checkRefresh(String clientKey) {
        check(refreshAttempts, normalize(clientKey), properties.getRefresh(), "Too many refresh attempts. Try again later.");
    }

    public void checkForgotPassword(String clientKey, String email) {
        check(forgotPasswordAttempts, normalize(clientKey) + "|" + normalize(email),
                properties.getForgotPassword(), "Too many password reset attempts. Try again later.");
    }

    public void checkResendVerification(String clientKey, String email) {
        check(resendVerificationAttempts, normalize(clientKey) + "|" + normalize(email),
                properties.getResendVerification(), "Too many verification resend attempts. Try again later.");
    }

    private static Cache<String, AtomicInteger> newCache(Duration window) {
        return Caffeine.newBuilder()
                .expireAfterWrite(window)
                .maximumSize(10_000)
                .build();
    }

    private static void check(Cache<String, AtomicInteger> cache,
                              String key,
                              AuthRateLimitProperties.Limit limit,
                              String message) {
        AtomicInteger attempts = cache.get(key, ignored -> new AtomicInteger(0));
        if (attempts.incrementAndGet() > limit.getMaxAttempts()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim().toLowerCase();
    }
}
