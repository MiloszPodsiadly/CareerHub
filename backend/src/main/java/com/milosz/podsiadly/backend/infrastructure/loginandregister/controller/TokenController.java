package com.milosz.podsiadly.backend.infrastructure.loginandregister.controller;

import com.milosz.podsiadly.backend.infrastructure.loginandregister.controller.dto.JwtResponseDto;
import com.milosz.podsiadly.backend.infrastructure.loginandregister.controller.dto.TokenRequestDto;
import com.milosz.podsiadly.backend.infrastructure.security.jwt.JwtAuthenticatorFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class TokenController {

    private final JwtAuthenticatorFacade jwt;

    @PostMapping("/token")
    public ResponseEntity<JwtResponseDto> token(@Valid @RequestBody TokenRequestDto req) {
        return ResponseEntity.ok(jwt.authenticateAndGenerateToken(req));
    }
}
