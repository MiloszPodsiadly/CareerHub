package com.milosz.podsiadly.backend.job.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "job_offer_skill")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JobOfferSkill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private JobOffer jobOffer;

    @Column(nullable = false)
    private String name;

    private String levelLabel;
    private Integer levelValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SkillSource source;
}
