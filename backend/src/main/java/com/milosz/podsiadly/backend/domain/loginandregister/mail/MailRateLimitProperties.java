package com.milosz.podsiadly.backend.domain.loginandregister.mail;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component("mailRateLimitProperties")
@ConfigurationProperties(prefix = "app.mailer.rate-limit")
public class MailRateLimitProperties {

    private Verification verification = new Verification();
    private Reset reset = new Reset();

    @Getter
    @Setter
    public static class Verification {
        private Duration minInterval = Duration.ofMinutes(1);
        private int maxPerDay = 5;
    }

    @Getter
    @Setter
    public static class Reset {
        private Duration minInterval = Duration.ofMinutes(1);
        private int maxPerDay = 5;
    }
}
