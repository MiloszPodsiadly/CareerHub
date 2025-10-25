package com.milosz.podsiadly.backend.domain.profile.dto;

import java.time.LocalDate;

public record ProfileDto(
        String userId,
        String name,
        String email,
        String about,
        LocalDate dob,
        String avatarUrl,
        String avatarPreset,
        String avatarFileId,
        String cvFileId
) {}
