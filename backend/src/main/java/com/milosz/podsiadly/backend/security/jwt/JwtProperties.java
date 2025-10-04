package com.milosz.podsiadly.backend.security.jwt;

import lombok.Getter; import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter @Setter
@ConfigurationProperties(prefix = "auth.jwt")
public class JwtProperties {
    private String secret;
    private String issuer;
    private int accessMinutes;  // 60
    private int refreshDays;    // 7
}
