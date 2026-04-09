package com.milosz.podsiadly.backend.integration.security;

import com.milosz.podsiadly.backend.domain.loginandregister.LoginRepository;
import com.milosz.podsiadly.backend.domain.loginandregister.Role;
import com.milosz.podsiadly.backend.domain.loginandregister.RoleRepository;
import com.milosz.podsiadly.backend.domain.loginandregister.User;
import com.milosz.podsiadly.backend.integration.BackendIntegrationTestBase;
import com.milosz.podsiadly.backend.security.jwt.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class SecurityConfigIntegrationTests extends BackendIntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private LoginRepository loginRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @AfterEach
    void cleanUp() {
        loginRepository.deleteAll();
    }

    @ParameterizedTest
    @MethodSource("requestCases")
    void should_apply_security_rules_for_http_routes(Case testCase) {
        User user = createUser("user@example.com", true, Set.of("ROLE_USER"));

        HttpHeaders headers = new HttpHeaders();
        if (testCase.auth() == AuthMode.BEARER_USER) {
            headers.setBearerAuth(accessTokenFor(user));
        }
        if (testCase.auth() == AuthMode.COOKIE_USER) {
            headers.add(HttpHeaders.COOKIE, "ACCESS=" + accessTokenFor(user));
        }

        ResponseEntity<String> response = restTemplate.exchange(
                testCase.path(),
                testCase.method(),
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(testCase.expected());
    }

    private static Stream<Case> requestCases() {
        return Stream.of(
                new Case("public jobs list", HttpMethod.GET, "/api/jobs", AuthMode.NONE, HttpStatus.OK),
                new Case("jobs mine rejected without auth", HttpMethod.GET, "/api/jobs/mine", AuthMode.NONE, HttpStatus.FORBIDDEN),
                new Case("jobs mine accepts bearer auth", HttpMethod.GET, "/api/jobs/mine", AuthMode.BEARER_USER, HttpStatus.OK),
                new Case("jobs mine rejects removed access cookie auth", HttpMethod.GET, "/api/jobs/mine", AuthMode.COOKIE_USER, HttpStatus.FORBIDDEN)
        );
    }

    private User createUser(String email, boolean verified, Set<String> roleNames) {
        List<Role> roles = roleNames.stream()
                .map(name -> roleRepository.findByName(name).orElseThrow())
                .toList();

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode("Password123!"))
                .emailVerified(verified)
                .enabled(true)
                .roles(Set.copyOf(roles))
                .build();

        return loginRepository.save(user);
    }

    private String accessTokenFor(User user) {
        List<String> roles = user.getRoles().stream().map(Role::getName).toList();
        return jwtService.issueAccess(user.getId(), user.getUsername(), roles);
    }

    private record Case(String name, HttpMethod method, String path, AuthMode auth, HttpStatus expected) {
        @Override
        public String toString() {
            return name;
        }
    }

    private enum AuthMode {
        NONE,
        BEARER_USER,
        COOKIE_USER
    }
}
