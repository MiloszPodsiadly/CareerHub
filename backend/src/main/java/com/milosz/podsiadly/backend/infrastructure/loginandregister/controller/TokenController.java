package com.milosz.podsiadly.backend.infrastructure.loginandregister.controller;

import com.milosz.podsiadly.backend.domain.loginandregister.*;
import com.milosz.podsiadly.backend.domain.loginandregister.dto.*;
import com.milosz.podsiadly.backend.domain.profile.ProfileRepository;
import com.milosz.podsiadly.backend.security.AuthCookieProperties;
import com.milosz.podsiadly.backend.security.AuthRateLimiter;
import com.milosz.podsiadly.backend.security.jwt.JwtProperties;
import com.milosz.podsiadly.backend.security.jwt.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;

import static com.milosz.podsiadly.backend.domain.loginandregister.LoginMapper.toMeDto;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class TokenController {

    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final LoginUserDetailsService usersByUsername;
    private final LoginRepository users;
    private final UserService userService;
    private final JwtProperties props;
    private final AuthCookieProperties cookieProperties;
    private final AuthRateLimiter authRateLimiter;
    private final ProfileRepository profiles;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

    public record LoginReq(
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            String email,
            @NotBlank(message = "Password is required")
            String password
    ) {}
    public record TokenRes(String accessToken) {}

    public record VerifyEmailRequest(
            @NotBlank(message = "Token is required")
            String token
    ) {}

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req,
                                               HttpServletRequest servletRequest) {
        authRateLimiter.checkForgotPassword(clientKey(servletRequest), req.email());
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
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        emailVerificationService.verify(req.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<TokenRes> login(@Valid @RequestBody LoginReq req,
                                          HttpServletRequest servletRequest,
                                          HttpServletResponse resp) {
        authRateLimiter.checkLogin(clientKey(servletRequest), req.email());
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password())
        );

        User u = usersByUsername.loadUserByUsername(req.email());

        if (!u.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "E-mail not verified");
        }

        String access  = jwt.issueAccess(
                u.getId(),
                u.getUsername(),
                u.getRoles().stream().map(Role::getName).toList()
        );
        String refresh = jwt.issueRefresh(u.getId());

        resp.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(refresh).toString());
        return ResponseEntity.ok(new TokenRes(access));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenRes> refresh(HttpServletRequest servletRequest) {
        authRateLimiter.checkRefresh(clientKey(servletRequest));
        String refreshToken = resolveRefreshCookie(servletRequest);
        var claims = jwt.parse(refreshToken).getBody();
        if (!"refresh".equals(claims.get("type"))) throw new BadCredentialsException("Invalid refresh token");

        var userId = claims.getSubject();
        User u = users.findById(userId).orElseThrow();

        if (!u.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "E-mail not verified");
        }

        String access = jwt.issueAccess(u.getId(), u.getUsername(), u.getRoles().stream().map(Role::getName).toList());
        return ResponseEntity.ok(new TokenRes(access));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse resp) {
        ResponseCookie del = ResponseCookie.from(cookieProperties.getRefreshName(), "")
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite(cookieProperties.getSameSite())
                .path(cookieProperties.getRefreshPath())
                .maxAge(0)
                .build();
        resp.addHeader(HttpHeaders.SET_COOKIE, del.toString());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeDto me(@AuthenticationPrincipal User user) {
        var p = profiles.findByUserId(user.getId()).orElse(null);
        return toMeDto(
                user,
                p != null ? p.getName()      : null,
                p != null ? p.getEmail()     : null,
                p != null ? p.getAvatarUrl() : null,
                p != null ? p.getAbout()      : null,
                p != null ? p.getDob()       : null
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handle(IllegalArgumentException e) {
        return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(cookieProperties.getRefreshName(), value)
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite(cookieProperties.getSameSite())
                .path(cookieProperties.getRefreshPath())
                .maxAge(Duration.ofDays(props.getRefreshDays()))
                .build();
    }

    public record ResendVerifyReq(
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            String email
    ) {}

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerifyReq req,
                                                   HttpServletRequest servletRequest) {
        authRateLimiter.checkResendVerification(clientKey(servletRequest), req.email());
        emailVerificationService.resend(req.email(), users);
        return ResponseEntity.ok().build();
    }


    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> onAuthFailure(AuthenticationException ex, HttpServletRequest request) {
        org.slf4j.LoggerFactory.getLogger(TokenController.class)
                .warn("[auth] authentication failed path={} client={} err={}",
                        request.getRequestURI(), clientKey(request), ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Incorrect username or password"));
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            throw new BadCredentialsException("Missing refresh token");
        }

        for (var cookie : request.getCookies()) {
            if (cookieProperties.getRefreshName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        throw new BadCredentialsException("Missing refresh token");
    }
}
