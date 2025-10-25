package com.milosz.podsiadly.backend.domain.file;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "file_object")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FileObject {

    @Id
    @UuidGenerator
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String filename;

    private String contentType;

    @Column(nullable = false)
    private long size;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    private byte[] data;
}
