package com.milosz.podsiadly.backend.job.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="company")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Company {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, unique=true) private String name;
}

