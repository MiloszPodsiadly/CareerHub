package com.milosz.podsiadly.backend.events.web;

import com.milosz.podsiadly.backend.events.service.EventIngestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/events/v1")
@RequiredArgsConstructor
public class EventIngestController {
    private final EventIngestService ingest;

    @PostMapping("/ingest")
    public String ingestAll() {
        int n = ingest.runAll();
        return "upserted: " + n;
    }
}
