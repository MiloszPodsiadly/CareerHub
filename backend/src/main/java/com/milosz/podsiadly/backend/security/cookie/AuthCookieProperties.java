package com.milosz.podsiadly.backend.security.cookie;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "auth.cookie")
public class AuthCookieProperties {
    private String refreshName = "REFRESH";
    private String refreshPath = "/api/auth/refresh";
    private String sameSite = "Lax";
    private boolean secure = false; // .yml override for environment
}