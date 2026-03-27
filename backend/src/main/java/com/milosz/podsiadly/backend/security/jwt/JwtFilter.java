package com.milosz.podsiadly.backend.security.jwt;

import com.milosz.podsiadly.backend.domain.loginandregister.LoginUserDetailsService;
import com.milosz.podsiadly.backend.domain.loginandregister.User;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";

    private final JwtService jwt;
    private final LoginUserDetailsService users;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest req,
            @NonNull HttpServletResponse res,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {

        if (HttpMethod.OPTIONS.matches(req.getMethod())) {
            chain.doFilter(req, res);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(req, res);
            return;
        }

        String token = resolveBearer(req);
        if (token == null) {
            chain.doFilter(req, res);
            return;
        }

        try {
            Claims c = jwt.parse(token).getBody();

            Object type = c.get(CLAIM_TYPE);
            if (!(type instanceof String t) || !TYPE_ACCESS.equals(t)) {
                chain.doFilter(req, res);
                return;
            }

            String email = c.getSubject();
            if (email == null || email.isBlank()) {
                chain.doFilter(req, res);
                return;
            }

            User u = users.loadUserByUsername(email);
            if (!u.isEnabled()) {
                chain.doFilter(req, res);
                return;
            }

            var auth = new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (Exception ex) {
            log.debug("JWT parse/auth failed (soft): {}", ex.toString());
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(req, res);
    }

    private String resolveBearer(HttpServletRequest req) {
        String h = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (h == null) return null;
        if (!h.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) return null;
        String token = h.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String p = req.getRequestURI();
        if (p == null) {
            return false;
        }
        return switch (p) {
            case "/api/auth/forgot-password",
                 "/api/auth/reset-password",
                 "/api/auth/register",
                 "/api/auth/verify-email",
                 "/api/auth/login",
                 "/api/auth/refresh",
                 "/api/auth/logout",
                 "/api/auth/resend-verification" -> true;
            default -> false;
        };
    }
}
