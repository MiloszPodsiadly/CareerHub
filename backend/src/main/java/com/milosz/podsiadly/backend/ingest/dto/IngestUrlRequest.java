package com.milosz.podsiadly.backend.ingest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record IngestUrlRequest(
        @NotBlank @URL String url,
        @Size(max = 50) String source
) {}