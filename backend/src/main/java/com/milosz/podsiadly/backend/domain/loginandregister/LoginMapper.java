package com.milosz.podsiadly.backend.domain.loginandregister;

import com.milosz.podsiadly.backend.domain.loginandregister.dto.MeDto;
import com.milosz.podsiadly.backend.domain.loginandregister.dto.UserDto;

import java.util.stream.Collectors;

public class LoginMapper {

    public static UserDto toDto(User u) {
        return new UserDto(
                u.getId(),
                u.getEmail(),
                u.getRoles().stream().map(Role::getName).collect(Collectors.toSet())
        );
    }

    public static MeDto toMeDto(
            User u,
            String name,
            String email,
            String avatarUrl,
            String about,
            java.time.LocalDate dob
    ) {
        return new MeDto(
                u.getId(),
                u.getUsername(),
                u.getRoles().stream().map(Role::getName).collect(Collectors.toSet()),
                name,
                email,
                avatarUrl,
                about,
                dob
        );
    }
}
