package com.milosz.podsiadly.backend.job.domain;

import com.milosz.podsiadly.backend.domain.loginandregister.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "job_offer_owner",
        uniqueConstraints = @UniqueConstraint(columnNames = "job_offer_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JobOfferOwner {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_offer_id")
    private JobOffer jobOffer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private Instant createdAt;
}
