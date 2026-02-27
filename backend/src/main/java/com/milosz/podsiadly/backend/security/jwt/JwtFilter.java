package com.milosz.podsiadly.backend.security.jwt;

import com.milosz.podsiadly.backend.domain.loginandregister.LoginUserDetailsService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
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
    private final JwtService jwt;
    private final LoginUserDetailsService users;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String token = resolveBearer(req);
        if (token == null) {
            chain.doFilter(req, res);
            return;
        }

        try {
            Claims c = jwt.parse(token).getBody();

            String email = (String) c.get("username");
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Missing username(email) claim");
            }

            var u = users.loadUserByUsername(email);

            if (!u.isEnabled()) {
                SecurityContextHolder.clearContext();
                chain.doFilter(req, res);
                return;
            }

            var auth = new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (Exception ex) {
            log.debug("JWT parse/auth failed: {}", ex.toString());
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(req, res);
    }

    private String resolveBearer(HttpServletRequest req) {
        String h = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (h != null && h.startsWith("Bearer ")) return h.substring(7);
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return false;
    }
}