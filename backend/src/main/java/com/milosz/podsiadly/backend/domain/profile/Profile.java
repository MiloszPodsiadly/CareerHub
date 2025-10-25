package com.milosz.podsiadly.backend.domain.profile;

import com.milosz.podsiadly.backend.domain.loginandregister.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "profiles")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Profile {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private String userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    private String name;
    private String email;

    @Column(name = "about", length = 2000)
    private String about;

    private LocalDate dob;

    private String avatarUrl;
    private String avatarPreset;
    @Column(name = "avatar_file_id")
    private String avatarFileId;

    @Column(name = "cv_file_id")
    private String cvFileId;

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
