package com.milosz.podsiadly.backend.domain.loginandregister;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LoginRepository extends JpaRepository<User, String> {
    Optional<User> findByUsername(String username);
}