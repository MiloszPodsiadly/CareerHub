package com.milosz.podsiadly.gateway.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties({GwJwtProperties.class, PublicPathsProperties.class})
public class JwtAuthServletFilter implements Filter {

    private final GwJwtProperties jwtProps;
    private final PublicPathsProperties publicPaths;
    private final AntPathMatcher matcher = new AntPathMatcher();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String path = req.getRequestURI();
        boolean isPublic = publicPaths.getPublicPaths().stream().anyMatch(p -> matcher.match(p, path));
        if (isPublic) {
            chain.doFilter(req, res);
            return;
        }

        String token = resolveToken(req);
        if (token == null) {
            unauthorized(res, "Missing token");
            return;
        }

        Claims claims;
        try {
            claims = Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(jwtProps.getSecret().getBytes(StandardCharsets.UTF_8)))
                    .requireIssuer(jwtProps.getIssuer())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException e) {
            unauthorized(res, "Invalid or expired token");
            return;
        }

        chain.doFilter(new JwtRequestWrapper(req, token, claims), res);
    }

    private String resolveToken(HttpServletRequest req) {
        String hdr = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (hdr != null && hdr.startsWith("Bearer ")) return hdr.substring(7);
        if (req.getCookies() != null) {
            return Arrays.stream(req.getCookies())
                    .filter(c -> "ACCESS".equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst().orElse(null);
        }
        return null;
    }

    private void unauthorized(HttpServletResponse res, String msg) throws IOException {
        res.setStatus(HttpStatus.UNAUTHORIZED.value());
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"" + msg + "\"}");
    }
}
