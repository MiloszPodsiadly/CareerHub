package com.milosz.podsiadly.backend.domain.loginandregister.mail;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component("sesMailProperties")
@ConfigurationProperties(prefix = "app.mailer.aws")
public class SesMailProperties {
    private String region;
    private String from;
}
