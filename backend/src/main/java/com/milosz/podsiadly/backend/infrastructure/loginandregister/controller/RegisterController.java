package com.milosz.podsiadly.backend.infrastructure.loginandregister.controller;


import com.milosz.podsiadly.backend.domain.loginandregister.LoginAndRegisterFacade;
import com.milosz.podsiadly.backend.domain.loginandregister.dto.RegisterUserDto;
import com.milosz.podsiadly.backend.domain.loginandregister.dto.RegistrationResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class RegisterController {

    private final LoginAndRegisterFacade facade;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<RegistrationResultDto> register(@RequestBody RegisterUserDto body) {
        String encoded = passwordEncoder.encode(body.password());
        RegistrationResultDto result = facade.register(new RegisterUserDto(body.username(), encoded));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
