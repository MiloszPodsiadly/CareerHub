package com.milosz.podsiadly.backend.domain.loginandregister;

import com.milosz.podsiadly.backend.domain.loginandregister.dto.*;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
public class LoginAndRegisterFacade {
    private static final String USER_NOT_FOUND = "User not found";
    private final LoginRepository repository;
    private final RoleRepository roleRepository;

    public UserDto findByUsername(String username) {
        return repository.findByUsername(username)
                .map(u -> new UserDto(u.getId(), u.getPassword(), u.getUsername()))
                .orElseThrow(() -> new BadCredentialsException(USER_NOT_FOUND));
    }

    public User findEntityByUsername(String username) {
        return repository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException(USER_NOT_FOUND));
    }

    public RegistrationResultDto register(RegisterUserDto dto) {
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_USER").build()));

        User user = User.builder()
                .username(dto.username())
                .password(dto.password())
                .build();
        user.getRoles().add(userRole);

        User saved = repository.save(user);
        return new RegistrationResultDto(saved.getId(), true, saved.getUsername());
    }
}