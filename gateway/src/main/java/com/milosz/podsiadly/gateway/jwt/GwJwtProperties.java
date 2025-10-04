package com.milosz.podsiadly.gateway.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "auth.jwt")
public class GwJwtProperties {
    private String secret;
    private String issuer;
}
