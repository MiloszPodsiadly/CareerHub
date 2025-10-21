package com.milosz.podsiadly.backend.job.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "job_offer_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JobOfferHistory {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false) private String source;
    @Column(nullable=false) private String externalId;
    @Column(nullable=false, columnDefinition = "text") private String url;

    @Column(nullable=false) private String title;

    private String companyName;
    private String cityName;
    private Boolean remote;
    private String level;

    private String contract;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "job_offer_history_contracts",
            joinColumns = @JoinColumn(name = "history_id")
    )
    @Column(name = "contract", nullable = false)
    private List<String> contracts = new ArrayList<>();

    private Integer salaryMin;
    private Integer salaryMax;
    private String currency;

    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    private ArchiveReason reason;

    private Instant archivedAt;
    private Instant deactivatedAt;

    @Column(columnDefinition = "text")
    private String snapshotJson;

    public void setContracts(List<String> c) {
        this.contracts.clear();
        if (c != null) this.contracts.addAll(c);
    }
}
