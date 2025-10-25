package com.milosz.podsiadly.backend.domain.loginandregister.dto;

import java.time.LocalDate;
import java.util.Set;

public record MeDto(
        String id,
        String username,
        Set<String> roles,
        String name,
        String email,
        String avatarUrl,
        String about,
        LocalDate dob
) {}
