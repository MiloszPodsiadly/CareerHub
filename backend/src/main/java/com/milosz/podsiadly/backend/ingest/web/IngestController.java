package com.milosz.podsiadly.backend.ingest.web;

import com.milosz.podsiadly.backend.ingest.config.IngestMessagingProperties;
import com.milosz.podsiadly.backend.ingest.dto.IngestSitemapRequest;
import com.milosz.podsiadly.backend.ingest.dto.IngestUrlRequest;
import com.milosz.podsiadly.backend.ingest.service.IngestPublisher;
import com.milosz.podsiadly.backend.ingest.service.IngestService;
import com.milosz.podsiadly.backend.job.domain.JobSource;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Optional;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class IngestController {

    private final IngestPublisher publisher;
    private final IngestMessagingProperties props;
    private final IngestService ingestService;

    @PostMapping("/url")
    public ResponseEntity<Void> enqueueUrl(@Valid @RequestBody IngestUrlRequest req) {
        JobSource source = resolveSource(req.source());

        publisher.publishUrl(req.url(), source);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/sitemap")
    public ResponseEntity<Long> enqueueSitemap(@Valid @RequestBody IngestSitemapRequest req) throws Exception {
        JobSource source = resolveSource(req.source());

        long count = ingestService.ingestSitemap(req.sitemapUrl(), source);
        return ResponseEntity.ok(count);
    }

    private JobSource resolveSource(String sourceRaw) {
        String src = Optional.ofNullable(sourceRaw)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .orElseGet(props::getSourceDefault);

        String normalized = src.toUpperCase(Locale.ROOT);

        try {
            return JobSource.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Invalid source: " + src + ". Allowed: " + String.join(", ", allowedSources())
            );
        }
    }

    private String[] allowedSources() {
        JobSource[] values = JobSource.values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) names[i] = values[i].name();
        return names;
    }
}