package com.milosz.podsiadly.backend.unit.security.jwt;

import com.milosz.podsiadly.backend.security.jwt.JwtProperties;
import com.milosz.podsiadly.backend.security.jwt.JwtService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class JwtServiceTests {

    @Test
    void should_issue_and_parse_access_token_with_expected_claims() {
        JwtService service = new JwtService(jwtProperties("issuer-a"));

        String token = service.issueAccess("user-1", "alice@example.com", List.of("ROLE_USER"));

        var claims = service.parse(token).getBody();

        assertThat(claims.getSubject()).isEqualTo("user-1");
        assertThat(claims.getIssuer()).isEqualTo("issuer-a");
        assertThat(claims.get("username", String.class)).isEqualTo("alice@example.com");
        assertThat(claims.get("type", String.class)).isEqualTo("access");
    }

    @Test
    void should_issue_refresh_token_marked_as_refresh() {
        JwtService service = new JwtService(jwtProperties("issuer-a"));

        String token = service.issueRefresh("user-2");

        assertThat(service.parse(token).getBody().get("type", String.class)).isEqualTo("refresh");
    }

    @Test
    void should_reject_token_when_expected_issuer_differs() {
        JwtService issuerA = new JwtService(jwtProperties("issuer-a"));
        JwtService issuerB = new JwtService(jwtProperties("issuer-b"));

        String token = issuerA.issueAccess("user-1", "alice@example.com", List.of("ROLE_USER"));

        assertThatThrownBy(() -> issuerB.parse(token))
                .isInstanceOf(JwtException.class);
    }

    private static JwtProperties jwtProperties(String issuer) {
        JwtProperties props = new JwtProperties();
        props.setSecret("1234567890123456789012345678901234567890123456789012345678901234");
        props.setIssuer(issuer);
        props.setAccessMinutes(60);
        props.setRefreshDays(7);
        return props;
    }
}
