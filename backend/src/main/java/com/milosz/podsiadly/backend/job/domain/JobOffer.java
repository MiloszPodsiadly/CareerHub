package com.milosz.podsiadly.backend.job.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(
        name = "job_offer",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_job_offer_source_external",
                columnNames = {"source","external_id"}
        )
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JobOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private String source;
    @Column(name="external_id", nullable = false) private String externalId;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "apply_url", columnDefinition = "text")
    private String applyUrl;

    @Column(nullable = false) private String title;
    @Column(columnDefinition = "text") private String description;

    @JsonIgnore
    @OneToOne(mappedBy = "jobOffer", fetch = FetchType.LAZY)
    private JobOfferOwner owner;

    @ManyToOne(fetch = FetchType.LAZY) private Company company;
    @ManyToOne(fetch = FetchType.LAZY) private City city;

    private Boolean remote;

    @Enumerated(EnumType.STRING) private JobLevel level;
    @Enumerated(EnumType.STRING) private ContractType contract;

    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "job_offer_contract", joinColumns = @JoinColumn(name = "job_offer_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "contract", nullable = false, length = 16)
    private Set<ContractType> contracts = new HashSet<>();

    private Integer salaryMin;
    private Integer salaryMax;
    private String currency;

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "job_offer_tags", joinColumns = @JoinColumn(name = "job_offer_id"))
    @Column(name = "tag", length = 64)
    private List<String> techTags = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "jobOffer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobOfferSkill> techStack = new ArrayList<>();

    private Instant publishedAt;
    private Instant lastSeenAt;
    private Boolean active;

    public void setTechTags(List<String> tags) {
        this.techTags.clear();
        if (tags != null) this.techTags.addAll(tags);
    }

    public void setTechStack(List<JobOfferSkill> stack) {
        this.techStack.forEach(s -> s.setJobOffer(null));
        this.techStack.clear();
        if (stack != null) {
            stack.forEach(s -> s.setJobOffer(this));
            this.techStack.addAll(stack);
        }
    }

    public void setContracts(Set<ContractType> set) {
        this.contracts.clear();
        if (set != null) this.contracts.addAll(set);
    }
}
