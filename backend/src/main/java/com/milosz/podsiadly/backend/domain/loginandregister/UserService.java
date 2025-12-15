package com.milosz.podsiadly.backend.domain.loginandregister;

import com.milosz.podsiadly.backend.domain.loginandregister.dto.RegisterUserDto;
import com.milosz.podsiadly.backend.domain.loginandregister.dto.UserDto;
import com.milosz.podsiadly.backend.domain.profile.ProfileService;
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
    private final ProfileService profileService;

    @Transactional
    public UserDto register(RegisterUserDto dto) {
        if (dto.email() == null || dto.email().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        String email = dto.email().trim().toLowerCase();

        if (users.existsByEmail(email))
            throw new IllegalArgumentException("Email already in use");

        User u = User.builder()
                .email(email)
                .password(encoder.encode(dto.password()))
                .build();
        u.getRoles().add(roleService.getOrThrow("ROLE_USER"));

        users.save(u);
        profileService.createFor(u);

        return toDto(u);
    }


    @Transactional
    public UserDto addRole(String userId, String roleName) {
        User u = users.findById(userId).orElseThrow();
        u.getRoles().add(roleService.getOrThrow(roleName));
        return toDto(u);
    }

    @Transactional(readOnly = true)
    public UserDto getByEmail(String email) {
        return users.findByEmail(email)
                .map(LoginMapper::toDto)
                .orElseThrow();
    }
}
