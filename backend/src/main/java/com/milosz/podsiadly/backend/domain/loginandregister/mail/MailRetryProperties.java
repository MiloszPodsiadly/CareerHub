package com.milosz.podsiadly.backend.domain.loginandregister.mail;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component("mailRetryProperties")
@ConfigurationProperties(prefix = "app.mailer.retry")
public class MailRetryProperties {
    private Duration after1 = Duration.ofMinutes(1);
    private Duration after5 = Duration.ofMinutes(5);
    private Duration after30 = Duration.ofMinutes(30);
}
