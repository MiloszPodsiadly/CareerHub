package com.milosz.podsiadly.backend.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtService {
    private final JwtProperties props;

    private byte[] key() { return props.getSecret().getBytes(StandardCharsets.UTF_8); }

    public String issueAccess(String userId, String username, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(userId)
                .setIssuer(props.getIssuer())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(props.getAccessMinutes(), ChronoUnit.MINUTES))) // ⬅︎ 60 min
                .addClaims(Map.of("username", username, "roles", roles, "type", "access"))
                .signWith(Keys.hmacShaKeyFor(key()), SignatureAlgorithm.HS256)
                .compact();
    }

    public String issueRefresh(String userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(userId)
                .setIssuer(props.getIssuer())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(props.getRefreshDays(), ChronoUnit.DAYS)))
                .claim("type", "refresh")
                .signWith(Keys.hmacShaKeyFor(key()), SignatureAlgorithm.HS256)
                .compact();
    }

    public Jws<Claims> parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(key()))
                .requireIssuer(props.getIssuer())
                .build()
                .parseClaimsJws(token);
    }
}
