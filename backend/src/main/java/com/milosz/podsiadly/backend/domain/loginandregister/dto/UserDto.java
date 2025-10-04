package com.milosz.podsiadly.backend.domain.loginandregister.dto;

import java.util.Set;

public record UserDto(String id, String username, Set<String> roles) { }
