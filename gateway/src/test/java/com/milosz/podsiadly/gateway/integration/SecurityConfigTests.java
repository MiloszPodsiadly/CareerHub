package com.milosz.podsiadly.gateway.integration;

import com.milosz.podsiadly.gateway.GatewayApplicationTests;
import com.milosz.podsiadly.gateway.jwt.GwJwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class SecurityConfigTests extends GatewayApplicationTests {

    @LocalServerPort int port;

    @Autowired TestRestTemplate rest;
    @Autowired GwJwtProperties props;

    private RestTemplate mockAdmin;
    private String mockBase;

    enum Auth { NONE, USER, ADMIN, INVALID, EXPIRED }

    record Case(
            String name,
            HttpMethod method,
            String path,
            Auth auth,
            HttpStatus expectedStatus,
            boolean shouldForward
    ) {}

    @BeforeEach
    void setUp() {
        mockAdmin = new RestTemplate();
        mockBase = "http://" + backendMock.getHost() + ":" + backendMock.getServerPort();

        mockAdmin.exchange(
                mockBase + "/mockserver/reset",
                HttpMethod.PUT,
                new HttpEntity<>("{}", json()),
                String.class
        );
    }

    static Stream<Case> cases() {
        return Stream.of(
                new Case("auth/** permitAll", HttpMethod.POST, "/api/auth/login", Auth.NONE, HttpStatus.OK, true),
                new Case("auth/** permitAll (GET)", HttpMethod.GET, "/api/auth/me", Auth.NONE, HttpStatus.OK, true),
                new Case("public GET permitAll", HttpMethod.GET, "/api/public/x", Auth.NONE, HttpStatus.OK, true),
                new Case("events GET permitAll", HttpMethod.GET, "/api/events/123", Auth.NONE, HttpStatus.OK, true),
                new Case("salary calculate POST permitAll", HttpMethod.POST, "/api/salary/calculate", Auth.NONE, HttpStatus.OK, true),
                new Case("salary report GET permitAll", HttpMethod.GET, "/api/salary/report/2026-01", Auth.NONE, HttpStatus.OK, true),
                new Case("favorites status GET permitAll", HttpMethod.GET, "/api/favorites/1/2/status", Auth.NONE, HttpStatus.OK, true),
                new Case("jobs GET permitAll", HttpMethod.GET, "/api/jobs", Auth.NONE, HttpStatus.OK, true),
                new Case("jobs fast GET permitAll", HttpMethod.GET, "/api/jobs/fast", Auth.NONE, HttpStatus.OK, true),
                new Case("jobs count GET permitAll", HttpMethod.GET, "/api/jobs/count", Auth.NONE, HttpStatus.OK, true),
                new Case("jobs all GET permitAll", HttpMethod.GET, "/api/jobs/all", Auth.NONE, HttpStatus.OK, true),
                new Case("jobs by-external GET permitAll", HttpMethod.GET, "/api/jobs/by-external/abc", Auth.NONE, HttpStatus.OK, true),
                new Case("jobs id GET permitAll", HttpMethod.GET, "/api/jobs/123", Auth.NONE, HttpStatus.OK, true),
                new Case("jobs mine requires auth", HttpMethod.GET, "/api/jobs/mine", Auth.NONE, HttpStatus.UNAUTHORIZED, false),
                new Case("jobs mine user ok", HttpMethod.GET, "/api/jobs/mine", Auth.USER, HttpStatus.OK, true),
                new Case("jobs POST requires auth", HttpMethod.POST, "/api/jobs", Auth.NONE, HttpStatus.UNAUTHORIZED, false),
                new Case("jobs POST user ok", HttpMethod.POST, "/api/jobs", Auth.USER, HttpStatus.CREATED, true),
                new Case("jobs PUT requires auth", HttpMethod.PUT, "/api/jobs/123", Auth.NONE, HttpStatus.UNAUTHORIZED, false),
                new Case("jobs PUT user ok", HttpMethod.PUT, "/api/jobs/123", Auth.USER, HttpStatus.OK, true),
                new Case("jobs DELETE requires auth", HttpMethod.DELETE, "/api/jobs/123", Auth.NONE, HttpStatus.UNAUTHORIZED, false),
                new Case("jobs DELETE user ok", HttpMethod.DELETE, "/api/jobs/123", Auth.USER, HttpStatus.NO_CONTENT, true),
                new Case("applications POST requires auth", HttpMethod.POST, "/api/applications", Auth.NONE, HttpStatus.UNAUTHORIZED, false),
                new Case("applications POST user ok", HttpMethod.POST, "/api/applications", Auth.USER, HttpStatus.CREATED, true),
                new Case("applications mine requires auth", HttpMethod.GET, "/api/applications/mine", Auth.NONE, HttpStatus.UNAUTHORIZED, false),
                new Case("applications mine user ok", HttpMethod.GET, "/api/applications/mine", Auth.USER, HttpStatus.OK, true),
                new Case("job-drafts/** requires auth", HttpMethod.GET, "/api/job-drafts/mine", Auth.NONE, HttpStatus.UNAUTHORIZED, false),
                new Case("job-drafts/** user ok", HttpMethod.GET, "/api/job-drafts/mine", Auth.USER, HttpStatus.OK, true),
                new Case("profile base requires auth", HttpMethod.GET, "/api/profile", Auth.NONE, HttpStatus.UNAUTHORIZED, false),
                new Case("profile base user ok", HttpMethod.GET, "/api/profile", Auth.USER, HttpStatus.OK, true),
                new Case("profile nested requires auth", HttpMethod.GET, "/api/profile/me", Auth.NONE, HttpStatus.UNAUTHORIZED, false),
                new Case("profile nested user ok", HttpMethod.GET, "/api/profile/me", Auth.USER, HttpStatus.OK, true),
                new Case("favorites write requires auth", HttpMethod.PUT, "/api/favorites/JOB/10", Auth.NONE, HttpStatus.UNAUTHORIZED, false),
                new Case("favorites write user ok", HttpMethod.PUT, "/api/favorites/JOB/10", Auth.USER, HttpStatus.NO_CONTENT, true),
                new Case("favorites mine requires auth", HttpMethod.GET, "/api/favorites/mine?type=JOB", Auth.NONE, HttpStatus.UNAUTHORIZED, false),
                new Case("favorites mine user ok", HttpMethod.GET, "/api/favorites/mine?type=JOB", Auth.USER, HttpStatus.OK, true),
                new Case("ingest url no token -> 401", HttpMethod.POST, "/api/ingest/url", Auth.NONE, HttpStatus.UNAUTHORIZED, false),
                new Case("ingest url user -> 403", HttpMethod.POST, "/api/ingest/url", Auth.USER, HttpStatus.FORBIDDEN, false),
                new Case("ingest url admin -> 202", HttpMethod.POST, "/api/ingest/url", Auth.ADMIN, HttpStatus.ACCEPTED, true),
                new Case("ingest sitemap user -> 403", HttpMethod.POST, "/api/ingest/sitemap", Auth.USER, HttpStatus.FORBIDDEN, false),
                new Case("ingest sitemap admin -> 202", HttpMethod.POST, "/api/ingest/sitemap", Auth.ADMIN, HttpStatus.ACCEPTED, true),
                new Case("invalid token -> 401 (not forwarded)", HttpMethod.GET, "/api/profile", Auth.INVALID, HttpStatus.UNAUTHORIZED, false),
                new Case("expired token -> 401 (not forwarded)", HttpMethod.GET, "/api/profile", Auth.EXPIRED, HttpStatus.UNAUTHORIZED, false),
                new Case("unknown path no token -> 401 (fallback)", HttpMethod.GET, "/api/unknown", Auth.NONE, HttpStatus.UNAUTHORIZED, false),
                new Case("unknown path user -> 200 (fallback)", HttpMethod.GET, "/api/unknown", Auth.USER, HttpStatus.OK, true)
        );
    }

    @ParameterizedTest(name = "{index}. {0}")
    @MethodSource("cases")
    void security_contract_is_enforced(Case c) {
        String token = switch (c.auth) {
            case NONE -> null;
            case USER -> jwt("user@example.com", List.of("ROLE_USER"), props.getSecret(), props.getIssuer(), Instant.now(), Instant.now().plusSeconds(3600));
            case ADMIN -> jwt("admin@example.com", List.of("ROLE_ADMIN"), props.getSecret(), props.getIssuer(), Instant.now(), Instant.now().plusSeconds(3600));
            case INVALID -> jwt("user@example.com", List.of("ROLE_USER"), "wrong-secret-wrong-secret-wrong-secret-wrong-secret-123456", props.getIssuer(), Instant.now(), Instant.now().plusSeconds(3600));
            case EXPIRED -> jwt("user@example.com", List.of("ROLE_USER"), props.getSecret(), props.getIssuer(), Instant.now().minusSeconds(3600), Instant.now().minusSeconds(10));
        };

        int downstreamStatus = switch (c.expectedStatus) {
            case CREATED -> 201;
            case ACCEPTED -> 202;
            case NO_CONTENT -> 204;
            default -> 200;
        };

        String path = stripQuery(c.path);

        mockAdmin.exchange(
                mockBase + "/mockserver/expectation",
                HttpMethod.PUT,
                new HttpEntity<>("""
                        {
                          "httpRequest": { "method": "%s", "path": "%s" },
                          "httpResponse": { "statusCode": %d, "body": "ok" }
                        }
                        """.formatted(c.method.name(), path, downstreamStatus), json()),
                String.class
        );

        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        if (c.method == HttpMethod.POST || c.method == HttpMethod.PUT || c.method == HttpMethod.PATCH) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }

        Object body = (c.method == HttpMethod.POST || c.method == HttpMethod.PUT || c.method == HttpMethod.PATCH) ? "{}" : null;

        ResponseEntity<String> res = rest.exchange(
                "http://localhost:" + port + c.path,
                c.method,
                new HttpEntity<>(body, headers),
                String.class
        );

        assertThat(res.getStatusCode()).as(c.name).isEqualTo(c.expectedStatus);

        int expectedCount = c.shouldForward ? 1 : 0;

        ResponseEntity<String> verify = mockAdmin.exchange(
                mockBase + "/mockserver/verify",
                HttpMethod.PUT,
                new HttpEntity<>("""
                        {
                          "httpRequest": { "method": "%s", "path": "%s" },
                          "times": { "atLeast": %d, "atMost": %d }
                        }
                        """.formatted(c.method.name(), path, expectedCount, expectedCount), json()),
                String.class
        );

        assertThat(verify.getStatusCode())
                .as("forwarding verify: %s %s expected=%d".formatted(c.method, path, expectedCount))
                .isEqualTo(HttpStatus.ACCEPTED);

        if (c.shouldForward && token != null && (c.auth == Auth.USER || c.auth == Auth.ADMIN)) {
            ResponseEntity<String> verifyHdr = mockAdmin.exchange(
                    mockBase + "/mockserver/verify",
                    HttpMethod.PUT,
                    new HttpEntity<>("""
                            {
                              "httpRequest": {
                                "method": "%s",
                                "path": "%s",
                                "headers": { "Authorization": [ "Bearer %s" ] }
                              },
                              "times": { "atLeast": 1, "atMost": 1 }
                            }
                            """.formatted(c.method.name(), path, escape(token)), json()),
                    String.class
            );

            assertThat(verifyHdr.getStatusCode())
                    .as("auth header forwarded")
                    .isEqualTo(HttpStatus.ACCEPTED);
        }
    }

    private static HttpHeaders json() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private static String stripQuery(String s) {
        int i = s.indexOf('?');
        return i >= 0 ? s.substring(0, i) : s;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String jwt(String subject, List<String> roles, String secret, String issuer, Instant iat, Instant exp) {
        return Jwts.builder()
                .setSubject(subject)
                .setIssuer(issuer)
                .setIssuedAt(Date.from(iat))
                .setExpiration(Date.from(exp))
                .claim("roles", roles)
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }
}