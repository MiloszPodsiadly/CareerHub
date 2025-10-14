package com.milosz.podsiadly.backend.events.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "dev_event",
        uniqueConstraints = @UniqueConstraint(name="ux_source_external", columnNames={"source","externalId"}),
        indexes = {
                @Index(name="ix_start",       columnList = "startAt"),
                @Index(name="ix_country_city",columnList = "country,city"),
                @Index(name="ix_fingerprint", columnList = "fingerprint")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TechEvent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String source;

    @Column(nullable = false, columnDefinition = "text")
    private String externalId;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String country;

    @Column(columnDefinition = "text")
    private String region;

    @Column(columnDefinition = "text")
    private String city;

    @Column(length = 100)
    private String timezone;

    private Boolean online;

    @Enumerated(EnumType.STRING)
    private EventType type;

    private Instant startAt;
    private Instant endAt;

    @Column(columnDefinition = "text")
    private String status;

    @Column(columnDefinition = "text")
    private String venue;

    private Double latitude;
    private Double longitude;

    @ElementCollection
    @CollectionTable(name = "dev_event_tags", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "tag", length = 128)
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    private Instant firstSeenAt;
    private Instant lastSeenAt;

    @Column(columnDefinition = "text")
    private String raw;

    @Column(length = 64)
    private String fingerprint;
}
