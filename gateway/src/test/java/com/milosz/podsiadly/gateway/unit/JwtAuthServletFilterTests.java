package com.milosz.podsiadly.gateway.unit;

import com.milosz.podsiadly.gateway.jwt.GwJwtProperties;
import com.milosz.podsiadly.gateway.jwt.JwtAuthServletFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class JwtAuthServletFilterTests {

    private static final String ISSUER = "test-issuer";
    private static final String SECRET = "not-a-secret-not-a-secret-not-a-secret-not-a-secret-123456";

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void no_header_passes_through_without_auth() throws Exception {
        var filter = new JwtAuthServletFilter(props());

        var out = run(filter, "GET", "/api/anything", null);

        assertThat(out.chainCalled).isTrue();
        assertThat(out.status).isEqualTo(200);
        assertThat(out.authInChain).isNull();
    }

    @Test
    void non_bearer_header_is_ignored() throws Exception {
        var filter = new JwtAuthServletFilter(props());

        var out = run(filter, "GET", "/api/anything", "Token abc");

        assertThat(out.chainCalled).isTrue();
        assertThat(out.status).isEqualTo(200);
        assertThat(out.authInChain).isNull();
    }

    @Test
    void invalid_token_returns_401_json_and_stops_chain() throws Exception {
        var filter = new JwtAuthServletFilter(props());

        var out = run(filter, "GET", "/api/anything", "Bearer invalid");

        assertThat(out.chainCalled).isFalse();
        assertThat(out.status).isEqualTo(401);
        assertThat(out.contentType).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(out.body).isEqualTo("{\"error\":\"Invalid or expired token\"}");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void expired_token_returns_401_and_stops_chain() throws Exception {
        var filter = new JwtAuthServletFilter(props());
        String token = jwt("user@example.com", ISSUER, SECRET,
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(60),
                List.of("ROLE_USER"));

        var out = run(filter, "GET", "/api/anything", "Bearer " + token);

        assertThat(out.chainCalled).isFalse();
        assertThat(out.status).isEqualTo(401);
    }

    @Test
    void wrong_issuer_returns_401_and_stops_chain() throws Exception {
        var filter = new JwtAuthServletFilter(props());
        String token = jwt("user@example.com", "wrong-issuer", SECRET,
                Instant.now(), Instant.now().plusSeconds(3600),
                List.of("ROLE_USER"));

        var out = run(filter, "GET", "/api/anything", "Bearer " + token);

        assertThat(out.chainCalled).isFalse();
        assertThat(out.status).isEqualTo(401);
    }

    @Test
    void valid_token_authenticates_and_preserves_authorization_header_downstream() throws Exception {
        var filter = new JwtAuthServletFilter(props());
        String token = jwt("user@example.com", ISSUER, SECRET,
                Instant.now(), Instant.now().plusSeconds(3600),
                List.of("ROLE_USER"));

        var out = run(filter, "GET", "/api/anything", "Bearer " + token);

        assertThat(out.chainCalled).isTrue();
        assertThat(out.status).isEqualTo(200);
        assertThat(out.authInChain).isNotNull();
        assertThat(out.authInChain.getName()).isEqualTo("user@example.com");
        assertThat(out.authInChain.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        assertThat(out.authorizationSeenInChain).isEqualTo("Bearer " + token);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missing_roles_claim_results_in_empty_authorities() throws Exception {
        var filter = new JwtAuthServletFilter(props());

        String token = Jwts.builder()
                .setSubject("user@example.com")
                .setIssuer(ISSUER)
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();

        var out = run(filter, "GET", "/api/anything", "Bearer " + token);

        assertThat(out.chainCalled).isTrue();
        assertThat(out.authInChain).isNotNull();
        assertThat(out.authInChain.getAuthorities()).isEmpty();
    }

    @Test
    void roles_claim_with_junk_is_safely_filtered() throws Exception {
        var filter = new JwtAuthServletFilter(props());

        String token = Jwts.builder()
                .setSubject("user@example.com")
                .setIssuer(ISSUER)
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
                .claim("roles", List.of("ROLE_USER", "", "   ", 123))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();

        var out = run(filter, "GET", "/api/anything", "Bearer " + token);

        assertThat(out.chainCalled).isTrue();
        assertThat(out.authInChain).isNotNull();
        assertThat(out.authInChain.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    void roles_claim_with_wrong_type_is_ignored() throws Exception {
        var filter = new JwtAuthServletFilter(props());

        String token = Jwts.builder()
                .setSubject("user@example.com")
                .setIssuer(ISSUER)
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
                .claim("roles", "ROLE_USER")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();

        var out = run(filter, "GET", "/api/anything", "Bearer " + token);

        assertThat(out.chainCalled).isTrue();
        assertThat(out.authInChain).isNotNull();
        assertThat(out.authInChain.getAuthorities()).isEmpty();
    }


    private static GwJwtProperties props() {
        var p = new GwJwtProperties();
        p.setIssuer(ISSUER);
        p.setSecret(SECRET);
        return p;
    }

    private static String jwt(String subject, String issuer, String secret,
                              Instant issuedAt, Instant expiresAt,
                              List<String> roles) {
        return Jwts.builder()
                .setSubject(subject)
                .setIssuer(issuer)
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(expiresAt))
                .claim("roles", roles)
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    private static Outcome run(JwtAuthServletFilter filter, String method, String path, String authHeader) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        if (authHeader != null) req.addHeader(HttpHeaders.AUTHORIZATION, authHeader);
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        AtomicReference<Authentication> authInChain = new AtomicReference<>();
        AtomicReference<String> authzSeen = new AtomicReference<>();

        FilterChain chain = (ServletRequest request, ServletResponse response) -> {
            chainCalled.set(true);
            authInChain.set(SecurityContextHolder.getContext().getAuthentication());
            authzSeen.set(((HttpServletRequest) request).getHeader(HttpHeaders.AUTHORIZATION));
        };

        filter.doFilter(req, res, chain);

        return new Outcome(
                chainCalled.get(),
                res.getStatus(),
                res.getContentType(),
                res.getContentAsString(),
                authInChain.get(),
                authzSeen.get()
        );
    }

    private record Outcome(
            boolean chainCalled,
            int status,
            String contentType,
            String body,
            Authentication authInChain,
            String authorizationSeenInChain
    ) {}
}