package com.milosz.podsiadly.backend.infrastructure.security.jwt;

import com.milosz.podsiadly.backend.domain.loginandregister.LoginAndRegisterFacade;
import com.milosz.podsiadly.backend.domain.loginandregister.User;
import lombok.AllArgsConstructor;
import org.springframework.security.core.userdetails.*;

@AllArgsConstructor
public class LoginUserDetailsService implements UserDetailsService {
    private final LoginAndRegisterFacade facade;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User u = facade.findEntityByUsername(username);
        return new org.springframework.security.core.userdetails.User(
                u.getUsername(), u.getPassword(), u.getAuthorities());
    }
}