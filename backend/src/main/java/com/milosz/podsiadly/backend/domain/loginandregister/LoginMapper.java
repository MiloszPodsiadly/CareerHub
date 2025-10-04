package com.milosz.podsiadly.backend.domain.loginandregister;

import com.milosz.podsiadly.backend.domain.loginandregister.dto.UserDto;

import java.util.stream.Collectors;

public class LoginMapper {
    public static UserDto toDto(User u) {
        return new UserDto(
                u.getId(),
                u.getUsername(),
                u.getRoles().stream()
                        .map(Role::getName)
                        .collect(Collectors.toSet())
        );
    }
}
