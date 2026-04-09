package com.milosz.podsiadly.backend.integration.auth;

import com.milosz.podsiadly.backend.domain.loginandregister.LoginRepository;
import com.milosz.podsiadly.backend.domain.loginandregister.Role;
import com.milosz.podsiadly.backend.domain.loginandregister.RoleRepository;
import com.milosz.podsiadly.backend.domain.loginandregister.User;
import com.milosz.podsiadly.backend.domain.loginandregister.EmailVerificationTokenRepository;
import com.milosz.podsiadly.backend.domain.loginandregister.PasswordResetTokenRepository;
import com.milosz.podsiadly.backend.domain.loginandregister.mail.MailSendLogRepository;
import com.milosz.podsiadly.backend.domain.profile.ProfileRepository;
import com.milosz.podsiadly.backend.integration.BackendIntegrationTestBase;
import com.milosz.podsiadly.backend.security.jwt.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class TokenControllerIntegrationTests extends BackendIntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private LoginRepository loginRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private MailSendLogRepository mailSendLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @AfterEach
    void cleanUp() {
        mailSendLogRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        emailVerificationTokenRepository.deleteAll();
        profileRepository.deleteAll();
        loginRepository.deleteAll();
    }

    @Test
    void should_register_user_and_create_profile() {
        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/auth/register",
                jsonEntity(Map.of("email", "NEW@Example.com", "password", "Password123!")),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        User user = loginRepository.findByEmail("new@example.com").orElseThrow();
        assertThat(profileRepository.findByUserId(user.getId())).isPresent();
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void should_login_verified_user_and_return_refresh_cookie() {
        createUser("verified@example.com", "Password123!", true, Set.of("ROLE_USER"));

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login",
                jsonEntity(Map.of("email", "verified@example.com", "password", "Password123!")),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("accessToken");
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("REFRESH=");
    }

    @Test
    void should_reject_login_when_email_is_not_verified() {
        createUser("blocked@example.com", "Password123!", false, Set.of("ROLE_USER"));

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login",
                jsonEntity(Map.of("email", "blocked@example.com", "password", "Password123!")),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("E-mail not verified");
    }

    @Test
    void should_issue_new_access_token_from_refresh_cookie() {
        User user = createUser("refresh@example.com", "Password123!", true, Set.of("ROLE_USER"));
        String refreshToken = jwtService.issueRefresh(user.getId());

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "REFRESH=" + refreshToken);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("accessToken");
    }

    private HttpEntity<Map<String, Object>> jsonEntity(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private User createUser(String email, String rawPassword, boolean verified, Set<String> roleNames) {
        List<Role> roles = roleNames.stream()
                .map(name -> roleRepository.findByName(name).orElseThrow())
                .toList();

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .emailVerified(verified)
                .enabled(true)
                .roles(Set.copyOf(roles))
                .build();

        return loginRepository.save(user);
    }
}
