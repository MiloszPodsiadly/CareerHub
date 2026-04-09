package com.milosz.podsiadly.backend.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "auth.cookie")
public class AuthCookieProperties {
    private String refreshName = "REFRESH";
    private String refreshPath = "/api/auth/refresh";
    private boolean secure = false;
    private String sameSite = "Lax";
}
