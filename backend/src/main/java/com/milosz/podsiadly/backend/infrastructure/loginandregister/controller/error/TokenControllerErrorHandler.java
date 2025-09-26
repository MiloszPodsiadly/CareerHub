package com.milosz.podsiadly.backend.infrastructure.loginandregister.controller.error;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class TokenControllerErrorHandler {

    private static final String BAD_CREDENTIALS = "Bad Credentials";

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(BadCredentialsException.class)
    public TokenErrorResponse handleBadCredentials() {
        return new TokenErrorResponse(BAD_CREDENTIALS, HttpStatus.UNAUTHORIZED);
    }
}