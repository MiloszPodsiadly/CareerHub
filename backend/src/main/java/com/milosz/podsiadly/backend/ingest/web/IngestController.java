package com.milosz.podsiadly.backend.ingest.web;

import com.milosz.podsiadly.backend.ingest.config.IngestMessagingProperties;
import com.milosz.podsiadly.backend.ingest.dto.IngestSitemapRequest;
import com.milosz.podsiadly.backend.ingest.dto.IngestUrlRequest;
import com.milosz.podsiadly.backend.ingest.service.IngestPublisher;
import com.milosz.podsiadly.backend.ingest.service.IngestService;
import com.milosz.podsiadly.backend.job.domain.JobSource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
@CrossOrigin
public class IngestController {

    private final IngestPublisher publisher;
    private final IngestMessagingProperties props;
    private final IngestService ingestService;

    @PostMapping("/url")
    public ResponseEntity<Void> enqueueUrl(@RequestBody IngestUrlRequest req) {
        String src = (req.source() != null && !req.source().isBlank())
                ? req.source()
                : props.getSourceDefault();

        JobSource source = JobSource.valueOf(src);

        publisher.publishUrl(req.url(), source);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/sitemap")
    public ResponseEntity<Long> enqueueSitemap(@RequestBody IngestSitemapRequest req) throws Exception {
        String src = (req.source() != null && !req.source().isBlank())
                ? req.source()
                : props.getSourceDefault();

        JobSource source = JobSource.valueOf(src);

        long count = ingestService.ingestSitemap(req.sitemapUrl(), source);
        return ResponseEntity.ok(count);
    }
}
