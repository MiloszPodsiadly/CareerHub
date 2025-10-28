package com.milosz.podsiadly.backend.job.domain;

import com.milosz.podsiadly.backend.domain.loginandregister.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "job_draft", indexes = {
        @Index(name = "ix_job_draft_owner_updated", columnList = "owner_id, updated_at DESC")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JobDraft {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    private String title;
    private String companyName;
    private String cityName;

    @Column(name = "payload_json", columnDefinition = "text", nullable = false)
    private String payloadJson;

    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;

    private Boolean published;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = (createdAt == null) ? now : createdAt;
        updatedAt = (updatedAt == null) ? now : updatedAt;
        if (payloadJson == null) payloadJson = "{}";
        if (published == null) published = false;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }
}