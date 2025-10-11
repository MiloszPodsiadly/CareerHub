package com.milosz.podsiadly.backend.job.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "job_offer_history",
        indexes = {
                @Index(name = "ix_hist_source_external", columnList = "source,externalId"),
                @Index(name = "ix_hist_archived_at", columnList = "archivedAt")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JobOfferHistory {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private String source;
    @Column(nullable = false) private String externalId;

    @Column(nullable = false, columnDefinition = "text") private String url;
    @Column(nullable = false) private String title;
    private String companyName;
    private String cityName;
    private Boolean remote;
    private String level;
    private String contract;
    private Integer salaryMin;
    private Integer salaryMax;
    private String currency;
    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArchiveReason reason;

    @Column(nullable = false)
    private Instant archivedAt;

    private Instant deactivatedAt;

    @Lob
    @Column(nullable = false, columnDefinition = "text")
    private String snapshotJson;
}
