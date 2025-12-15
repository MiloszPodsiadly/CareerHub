package com.milosz.podsiadly.backend.domain.loginandregister.dto;

import java.util.Set;

public record UserDto(String id, String email, Set<String> roles) { }
