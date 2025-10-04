package com.milosz.podsiadly.backend.domain.loginandregister;

import com.milosz.podsiadly.backend.domain.loginandregister.dto.RegisterUserDto;
import com.milosz.podsiadly.backend.domain.loginandregister.dto.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.milosz.podsiadly.backend.domain.loginandregister.LoginMapper.toDto;

@Service
@RequiredArgsConstructor
public class UserService {
    private final LoginRepository users;
    private final RoleService roleService;
    private final PasswordEncoder encoder;

    @Transactional
    public UserDto register(RegisterUserDto dto) {
        if (users.existsByUsername(dto.username()))
            throw new IllegalArgumentException("Username taken");

        User u = User.builder()
                .username(dto.username())
                .password(encoder.encode(dto.password()))
                .build();
        u.getRoles().add(roleService.getOrThrow("ROLE_USER"));
        users.save(u);
        return toDto(u);
    }

    @Transactional
    public UserDto addRole(String userId, String roleName) {
        User u = users.findById(userId).orElseThrow();
        u.getRoles().add(roleService.getOrThrow(roleName));
        return toDto(u);
    }

    @Transactional(readOnly = true)
    public UserDto getByUsername(String username) {
        return users.findByUsername(username).map(LoginMapper::toDto).orElseThrow();
    }
}
