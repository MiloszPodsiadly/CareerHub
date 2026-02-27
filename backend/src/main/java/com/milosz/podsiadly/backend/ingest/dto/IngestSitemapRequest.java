package com.milosz.podsiadly.backend.ingest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record IngestSitemapRequest(
        @NotBlank @URL String sitemapUrl,
        @Size(max = 50) String source
) {}