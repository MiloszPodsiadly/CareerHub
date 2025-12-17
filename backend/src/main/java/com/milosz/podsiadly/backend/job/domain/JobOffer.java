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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobSource source;

    @Column(name="external_id", nullable = false)
    private String externalId;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "apply_url", columnDefinition = "text")
    private String applyUrl;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @JsonIgnore
    @OneToOne(mappedBy = "jobOffer", fetch = FetchType.LAZY)
    private JobOfferOwner owner;

    @ManyToOne(fetch = FetchType.LAZY)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    private City city;

    private Boolean remote;

    @Enumerated(EnumType.STRING)
    private JobLevel level;

    @Enumerated(EnumType.STRING)
    private ContractType contract;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "job_offer_contract", joinColumns = @JoinColumn(name = "job_offer_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "contract", nullable = false, length = 16)
    private Set<ContractType> contracts = new HashSet<>();

    private Integer salaryMin;
    private Integer salaryMax;
    private String currency;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "job_offer_tags", joinColumns = @JoinColumn(name = "job_offer_id"))
    @Column(name = "tag", length = 64)
    private List<String> techTags = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "jobOffer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<JobOfferSkill> techStack = new ArrayList<>();

    private Instant publishedAt;
    private Instant lastSeenAt;
    private Boolean active;

    public void setTechTags(List<String> tags) {
        // this one is OK to replace (ElementCollection), but we can still be consistent
        this.techTags = (tags != null) ? new ArrayList<>(tags) : new ArrayList<>();
    }

    public void setContracts(Set<ContractType> set) {
        this.contracts = (set != null) ? new HashSet<>(set) : new HashSet<>();
    }

    /**
     * OrphanRemoval-safe replace: do NOT replace the list reference.
     */
    public void replaceTechStack(List<JobOfferSkill> stack) {
        if (this.techStack == null) this.techStack = new ArrayList<>();

        this.techStack.clear();

        if (stack == null || stack.isEmpty()) return;

        for (JobOfferSkill s : stack) {
            if (s == null) continue;
            s.setJobOffer(this);
            this.techStack.add(s);
        }
    }

    /**
     * Used by mapper safe-guard.
     */
    public void setTechStackIfNull() {
        if (this.techStack == null) this.techStack = new ArrayList<>();
    }

    /**
     * IMPORTANT:
     * Remove/avoid the old setter that replaced reference:
     * public void setTechStack(List<JobOfferSkill> stack) { this.techStack = fresh; }
     *
     * If you *must* keep a setter (for Lombok / frameworks), make it call replaceTechStack.
     */
    public void setTechStack(List<JobOfferSkill> stack) {
        replaceTechStack(stack);
    }
}
