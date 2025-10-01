package com.milosz.podsiadly.backend.infrastructure.security.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component @AllArgsConstructor @Slf4j
public class JwtAuthTokenFilter extends OncePerRequestFilter {

    private final JwtConfigurationProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        try {
            UsernamePasswordAuthenticationToken authentication = buildAuthentication(auth);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("JWT authenticated user='{}' roles={}",
                    authentication.getName(),
                    authentication.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toList()));
        } catch (Exception ex) {
            log.warn("JWT verification failed: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }

    private UsernamePasswordAuthenticationToken buildAuthentication(String header) {
        String token = header.substring(7);
        Algorithm algorithm = Algorithm.HMAC256(properties.secret());
        JWTVerifier verifier = JWT.require(algorithm).withIssuer(properties.issuer()).build();
        DecodedJWT jwt = verifier.verify(token);

        String username = jwt.getSubject();
        List<String> roles = jwt.getClaim("roles").asList(String.class);
        var authorities = roles == null ? Collections.<SimpleGrantedAuthority>emptyList()
                : roles.stream().map(SimpleGrantedAuthority::new).toList();

        return new UsernamePasswordAuthenticationToken(username, null, authorities);
    }
}
