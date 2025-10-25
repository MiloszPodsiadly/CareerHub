package com.milosz.podsiadly.backend.domain.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfileRepository extends JpaRepository<Profile, String> {
    Optional<Profile> findByUserId(String userId);
    boolean existsByUserId(String userId);
}
