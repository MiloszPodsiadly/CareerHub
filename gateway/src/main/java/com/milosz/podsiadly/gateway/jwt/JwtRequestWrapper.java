package com.milosz.podsiadly.gateway.jwt;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.http.HttpHeaders;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class JwtRequestWrapper extends HttpServletRequestWrapper {

    private final String token;
    private final Claims claims;

    public JwtRequestWrapper(HttpServletRequest request, String token, Claims claims) {
        super(request);
        this.token = token;
        this.claims = claims;
    }

    @Override
    public String getHeader(String name) {
        if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
            return "Bearer " + token;
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
            return Collections.enumeration(List.of("Bearer " + token));
        }
        return super.getHeaders(name);
    }

    public Claims getClaims() {
        return claims;
    }
}
