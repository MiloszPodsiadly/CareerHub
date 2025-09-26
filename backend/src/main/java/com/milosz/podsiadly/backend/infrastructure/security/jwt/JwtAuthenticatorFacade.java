package com.milosz.podsiadly.backend.infrastructure.security.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.milosz.podsiadly.backend.infrastructure.loginandregister.controller.dto.*;
import java.time.*;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

@AllArgsConstructor @Component @Slf4j
public class JwtAuthenticatorFacade {

    private final AuthenticationManager authenticationManager;
    private final Clock clock;
    private final JwtConfigurationProperties properties;

    public JwtResponseDto authenticateAndGenerateToken(TokenRequestDto loginRequest) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.username(), loginRequest.password()));
        User user = (User) auth.getPrincipal();
        String token = createToken(user);
        log.info("JWT issued for user='{}' roles={}", user.getUsername(),
                user.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toList()));
        return JwtResponseDto.builder().token(token).username(user.getUsername()).build();
    }

    private String createToken(User user) {
        String secretKey = properties.secret();
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        Instant now = LocalDateTime.now(clock).toInstant(ZoneOffset.UTC);
        Instant exp = now.plus(Duration.ofDays(properties.expirationDays()));
        var roles = user.getAuthorities().stream().map(a -> a.getAuthority()).toList();

        return JWT.create()
                .withSubject(user.getUsername())
                .withClaim("roles", roles)
                .withIssuedAt(now)
                .withExpiresAt(exp)
                .withIssuer(properties.issuer())
                .sign(algorithm);
    }
}

