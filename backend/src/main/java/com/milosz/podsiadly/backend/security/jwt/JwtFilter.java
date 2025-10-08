package com.milosz.podsiadly.backend.security.jwt;


import com.milosz.podsiadly.backend.domain.loginandregister.LoginUserDetailsService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final LoginUserDetailsService users;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String token = resolve(req);
        if (token != null) {
            try {
                Claims c = jwt.parse(token).getBody();
                var u = users.loadUserByUsername((String)c.get("username"));
                var auth = new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ignored) {}
        }
        chain.doFilter(req, res);
    }

    private String resolve(HttpServletRequest req) {
        String h = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (h != null && h.startsWith("Bearer ")) return h.substring(7);
        if (req.getCookies()!=null)
            return Arrays.stream(req.getCookies()).filter(c -> "ACCESS".equals(c.getName()))
                    .map(Cookie::getValue).findFirst().orElse(null);
        return null;
    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String uri = req.getRequestURI();
        return uri.startsWith("/api/auth/")
                || uri.startsWith("/api/public/")
                || uri.equals("/api/jobs")   || uri.startsWith("/api/jobs/")
                || uri.equals("/api/ingest") || uri.startsWith("/api/ingest/");
    }


}
