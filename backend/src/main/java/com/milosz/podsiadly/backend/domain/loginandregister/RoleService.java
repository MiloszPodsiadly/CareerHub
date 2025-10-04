package com.milosz.podsiadly.backend.domain.loginandregister;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roles;

    public Role getOrThrow(String name) {
        return roles.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + name));
    }

    @PostConstruct
    void initDefaultRoles() {
        if (roles.findByName("ROLE_USER").isEmpty())
            roles.save(Role.builder().name("ROLE_USER").build());
        if (roles.findByName("ROLE_ADMIN").isEmpty())
            roles.save(Role.builder().name("ROLE_ADMIN").build());
    }
}
