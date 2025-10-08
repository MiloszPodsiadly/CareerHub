package com.milosz.podsiadly.backend.ingest.web;

import com.milosz.podsiadly.backend.ingest.dto.IngestSitemapRequest;
import com.milosz.podsiadly.backend.ingest.dto.IngestUrlRequest;
import com.milosz.podsiadly.backend.ingest.service.IngestPublisher;
import com.milosz.podsiadly.backend.ingest.config.IngestMessagingProperties;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
@CrossOrigin
public class IngestController {

    private final IngestPublisher publisher;
    private final IngestMessagingProperties props;

    @PostMapping("/url")
    public ResponseEntity<Void> enqueueUrl(@RequestBody IngestUrlRequest req) {
        String src = req.source() != null ? req.source() : props.getSourceDefault();
        publisher.publishUrl(req.url(), src);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/sitemap")
    public ResponseEntity<Integer> enqueueSitemap(@RequestBody IngestSitemapRequest req) throws Exception {
        String src = req.source() != null ? req.source() : props.getSourceDefault();

        Document doc = Jsoup.connect(req.sitemapUrl())
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127 Safari/537.36")
                .referrer("https://www.google.com")
                .ignoreContentType(true)
                .timeout(15000)
                .get();

        List<String> urls = doc.select("url > loc").eachText();
        urls.forEach(u -> publisher.publishUrl(u, src));
        return ResponseEntity.ok(urls.size());
    }
}
