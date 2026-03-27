package com.milosz.podsiadly.backend.infrastructure.loginandregister.controller;

import com.milosz.podsiadly.backend.domain.loginandregister.*;
import com.milosz.podsiadly.backend.domain.loginandregister.dto.*;
import com.milosz.podsiadly.backend.domain.profile.ProfileRepository;
import com.milosz.podsiadly.backend.security.cookie.AuthCookieProperties;
import com.milosz.podsiadly.backend.security.jwt.JwtProperties;
import com.milosz.podsiadly.backend.security.jwt.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;

import static com.milosz.podsiadly.backend.domain.loginandregister.LoginMapper.toMeDto;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class TokenController {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_REFRESH = "refresh";

    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final LoginUserDetailsService usersByUsername;
    private final LoginRepository users;
    private final UserService userService;
    private final JwtProperties props;
    private final ProfileRepository profiles;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;
    private final AuthCookieProperties cookieProps;

    public record LoginReq(String email, String password) {}
    public record TokenRes(String accessToken) {}
    public record VerifyEmailRequest(String token) {}
    public record ResendVerifyReq(String email) {}

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        passwordResetService.sendResetLink(req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        passwordResetService.resetPassword(req);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterUserDto dto) {
        UserDto created = userService.register(dto);
        User u = usersByUsername.loadUserByUsername(created.email());
        emailVerificationService.sendVerificationLink(u);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestBody VerifyEmailRequest req) {
        emailVerificationService.verify(req.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<TokenRes> login(@RequestBody LoginReq req, HttpServletResponse resp) {
        String email = req.email() == null ? "" : req.email().trim().toLowerCase();
        String password = req.password() == null ? "" : req.password();

        authManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));

        User u = usersByUsername.loadUserByUsername(email);

        if (!u.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "E-mail not verified");
        }

        String access = jwt.issueAccess(
                u.getId(),
                u.getUsername(),
                u.getRoles().stream().map(Role::getName).toList()
        );

        String refresh = jwt.issueRefresh(u.getId());
        resp.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(refresh).toString());

        return ResponseEntity.ok(new TokenRes(access));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenRes> refresh(
            @CookieValue(value = "REFRESH", required = false) String refreshToken,
            HttpServletResponse resp
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing refresh token");
        }

        var claims = jwt.parse(refreshToken).getBody();

        Object type = claims.get(CLAIM_TYPE);
        if (!(type instanceof String t) || !TYPE_REFRESH.equals(t)) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        String userId = claims.getSubject();
        if (userId == null || userId.isBlank()) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        User u = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (!u.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "E-mail not verified");
        }

        String access = jwt.issueAccess(
                u.getId(),
                u.getUsername(),
                u.getRoles().stream().map(Role::getName).toList()
        );

        // ✅ PRO: refresh rotation (polecam)
        String newRefresh = jwt.issueRefresh(u.getId());
        resp.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(newRefresh).toString());

        return ResponseEntity.ok(new TokenRes(access));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse resp) {
        resp.addHeader(HttpHeaders.SET_COOKIE, deleteRefreshCookie().toString());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeDto me(@AuthenticationPrincipal User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        var p = profiles.findByUserId(user.getId()).orElse(null);
        return toMeDto(
                user,
                p != null ? p.getName() : null,
                p != null ? p.getEmail() : null,
                p != null ? p.getAvatarUrl() : null,
                p != null ? p.getAbout() : null,
                p != null ? p.getDob() : null
        );
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@RequestBody ResendVerifyReq req) {
        emailVerificationService.resend(req.email(), users);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handle(IllegalArgumentException e) {
        return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> onAuthFailure(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Incorrect username or password"));
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(cookieProps.getRefreshName(), value)
                .httpOnly(true)
                .secure(cookieProps.isSecure())
                .sameSite(cookieProps.getSameSite())
                .path(cookieProps.getRefreshPath())
                .maxAge(Duration.ofDays(props.getRefreshDays()))
                .build();
    }

    private ResponseCookie deleteRefreshCookie() {
        return ResponseCookie.from(cookieProps.getRefreshName(), "")
                .httpOnly(true)
                .secure(cookieProps.isSecure())
                .sameSite(cookieProps.getSameSite())
                .path(cookieProps.getRefreshPath())
                .maxAge(Duration.ZERO)
                .build();
    }
}
