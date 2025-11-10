package com.milosz.podsiadly.backend.infrastructure;

import jakarta.persistence.PersistenceException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
            DataIntegrityViolationException.class,
            ConstraintViolationException.class
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
