package com.milosz.podsiadly.backend.infrastructure.loginandregister.controller;

import com.milosz.podsiadly.backend.domain.loginandregister.*;
import com.milosz.podsiadly.backend.domain.loginandregister.dto.*;
import com.milosz.podsiadly.backend.domain.profile.ProfileRepository; // <--- DODAJ
import com.milosz.podsiadly.backend.security.jwt.JwtProperties;
import com.milosz.podsiadly.backend.security.jwt.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.Map;

import static com.milosz.podsiadly.backend.domain.loginandregister.LoginMapper.toDto;
import static com.milosz.podsiadly.backend.domain.loginandregister.LoginMapper.toMeDto; // <---

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
    private final ProfileRepository profiles;

    public record LoginReq(String username, String password) {}
    public record TokenRes(String accessToken) {}

    @PostMapping("/register")
    public ResponseEntity<TokenRes> register(@RequestBody RegisterUserDto dto, HttpServletResponse resp) {
        UserDto created = userService.register(dto);
        User u = usersByUsername.loadUserByUsername(created.username());

        String access  = jwt.issueAccess(u.getId(), u.getUsername(), u.getRoles().stream().map(Role::getName).toList());
        String refresh = jwt.issueRefresh(u.getId());

        resp.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(refresh).toString());
        return ResponseEntity.ok(new TokenRes(access));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenRes> login(@RequestBody LoginReq req, HttpServletResponse resp) {
        authManager.authenticate(new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        User u = usersByUsername.loadUserByUsername(req.username());

        String access  = jwt.issueAccess(u.getId(), u.getUsername(), u.getRoles().stream().map(Role::getName).toList());
        String refresh = jwt.issueRefresh(u.getId());

        resp.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(refresh).toString());
        return ResponseEntity.ok(new TokenRes(access));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenRes> refresh(@CookieValue("REFRESH") String refreshToken) {
        var claims = jwt.parse(refreshToken).getBody();
        if (!"refresh".equals(claims.get("type"))) throw new BadCredentialsException("Invalid refresh token");

        var userId = claims.getSubject();
        User u = users.findById(userId).orElseThrow();
        String access = jwt.issueAccess(u.getId(), u.getUsername(), u.getRoles().stream().map(Role::getName).toList());
        return ResponseEntity.ok(new TokenRes(access));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse resp) {
        ResponseCookie del = ResponseCookie.from("REFRESH","")
                .httpOnly(true).secure(false).sameSite("Lax")
                .path("/api/auth").maxAge(0).build();
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
        return ResponseCookie.from("REFRESH", value)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(Duration.ofDays(props.getRefreshDays()))
                .build();
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> onAuthFailure(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Incorrect username or password"));
    }
}
