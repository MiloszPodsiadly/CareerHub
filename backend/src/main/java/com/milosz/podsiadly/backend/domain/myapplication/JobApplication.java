package com.milosz.podsiadly.backend.domain.myapplication;

import com.milosz.podsiadly.backend.domain.loginandregister.User;
import com.milosz.podsiadly.backend.job.domain.JobOffer;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "job_application",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_job_application_applicant_offer",
                columnNames = {"applicant_id","offer_id"}
        ),
        indexes = {
                @Index(name="ix_application_applicant_created", columnList="applicant_id, created_at DESC"),
                @Index(name="ix_application_offer_created",     columnList="offer_id, created_at DESC")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JobApplication {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_id", nullable = true)
    private JobOffer offer;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id")
    private User applicant;

    @Column(name = "cv_file_id")
    private String cvFileId;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "apply_url")
    private String applyUrl;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (status == null) status = ApplicationStatus.APPLIED;
        if (createdAt == null) createdAt = Instant.now();
    }
}
