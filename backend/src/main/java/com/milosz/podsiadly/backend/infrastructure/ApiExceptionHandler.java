package com.milosz.podsiadly.backend.infrastructure;

import jakarta.persistence.PersistenceException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    record ApiError(String message) {}

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleRse(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ApiError(ex.getReason() != null ? ex.getReason() : "Error"));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiError> handleValidation(Exception ex) {
        String message;

        if (ex instanceof MethodArgumentNotValidException manv) {
            message = manv.getBindingResult().getFieldErrors().stream()
                    .map(err -> err.getField() + ": " + err.getDefaultMessage())
                    .distinct()
                    .collect(Collectors.joining("; "));
        } else if (ex instanceof BindException be) {
            message = be.getBindingResult().getFieldErrors().stream()
                    .map(err -> err.getField() + ": " + err.getDefaultMessage())
                    .distinct()
                    .collect(Collectors.joining("; "));
        } else if (ex instanceof ConstraintViolationException cve) {
            message = cve.getConstraintViolations().stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .distinct()
                    .collect(Collectors.joining("; "));
        } else if (ex instanceof MethodArgumentTypeMismatchException matme) {
            message = matme.getName() + ": invalid value";
        } else {
            message = "Validation failed";
        }

        if (message == null || message.isBlank()) {
            message = "Validation failed";
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(message));
    }

    @ExceptionHandler({
            DataIntegrityViolationException.class,
            org.hibernate.exception.ConstraintViolationException.class
    })
    public ResponseEntity<ApiError> handleConflict(RuntimeException ex) {
        log.debug("Conflict mapped to 409", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("You have already applied to this offer."));
    }

    @ExceptionHandler(PersistenceException.class)
    public ResponseEntity<ApiError> handlePersistence(PersistenceException ex) {
        if (ex.getCause() instanceof ConstraintViolationException) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("You have already applied to this offer."));
        }
        log.error("Unexpected JPA error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("Unexpected error"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("Unexpected error"));
    }
}
