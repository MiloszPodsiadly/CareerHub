package com.milosz.podsiadly.gateway.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(GwJwtProperties.class)
public class JwtAuthServletFilter implements Filter {

    private final GwJwtProperties jwtProps;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String token = resolveToken(req);
        try {
            if (token == null) {
                chain.doFilter(req, res);
                return;
            }

            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(jwtProps.getSecret().getBytes(StandardCharsets.UTF_8)))
                    .requireIssuer(jwtProps.getIssuer())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            List<GrantedAuthority> authorities = new ArrayList<>();
            Object rolesObj = claims.get("roles");
            if (rolesObj instanceof List<?> list) {
                for (Object r : list) {
                    if (r instanceof String role && !role.isBlank()) {
                        authorities.add(new SimpleGrantedAuthority(role));
                    }
                }
            }

            String principal = claims.getSubject();
            var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
            SecurityContextHolder.getContext().setAuthentication(auth);

            chain.doFilter(new JwtRequestWrapper(req, token), res);
        } catch (JwtException e) {
            SecurityContextHolder.clearContext();
            unauthorized(res, "Invalid or expired token");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String resolveToken(HttpServletRequest req) {
        String hdr = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (hdr != null && hdr.startsWith("Bearer ")) return hdr.substring(7);
        return null;
    }

    private void unauthorized(HttpServletResponse res, String msg) throws IOException {
        res.setStatus(HttpStatus.UNAUTHORIZED.value());
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"" + msg + "\"}");
    }
}
