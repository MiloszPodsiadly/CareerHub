package com.milosz.podsiadly.backend.job.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="city")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class City {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String name;
    private String countryCode;
}
