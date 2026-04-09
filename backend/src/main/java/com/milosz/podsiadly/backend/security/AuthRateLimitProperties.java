package com.milosz.podsiadly.backend.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "auth.rate-limit")
public class AuthRateLimitProperties {
    private Limit login = new Limit(Duration.ofMinutes(1), 10);
    private Limit refresh = new Limit(Duration.ofMinutes(1), 20);
    private Limit forgotPassword = new Limit(Duration.ofMinutes(15), 5);
    private Limit resendVerification = new Limit(Duration.ofMinutes(15), 5);

    @Getter
    @Setter
    public static class Limit {
        private Duration window = Duration.ofMinutes(1);
        private int maxAttempts = 10;

        public Limit() {
        }

        public Limit(Duration window, int maxAttempts) {
            this.window = window;
            this.maxAttempts = maxAttempts;
        }
    }
}
