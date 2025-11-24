package com.milosz.podsiadly.backend.job.controller;

import com.milosz.podsiadly.backend.domain.loginandregister.User;
import com.milosz.podsiadly.backend.job.dto.JobDraftDto;
import com.milosz.podsiadly.backend.job.dto.JobOfferDetailDto;
import com.milosz.podsiadly.backend.job.dto.JobDraftUpsertRequest;
import com.milosz.podsiadly.backend.job.service.JobDraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/job-drafts")
@RequiredArgsConstructor
public class JobDraftController {

    private final JobDraftService drafts;

    @Value("${app.platform.base-url:http://localhost:3000}")
    private String platformBaseUrl;

    @PostMapping
    public JobDraftDto createEmpty(@AuthenticationPrincipal User user) {
        if (user == null) throw new IllegalStateException("Unauthorized");
        return drafts.createEmpty(user);
    }

    @PatchMapping("/{id}")
    public JobDraftDto upsert(@AuthenticationPrincipal User user,
                              @PathVariable Long id,
                              @RequestBody JobDraftUpsertRequest req) {
        if (user == null) throw new IllegalStateException("Unauthorized");
        return drafts.upsert(user, id, req);
    }

    @GetMapping("/latest")
    public JobDraftDto latest(@AuthenticationPrincipal User user) {
        if (user == null) throw new IllegalStateException("Unauthorized");
        return drafts.latest(user);
    }

    @GetMapping("/mine")
    public List<JobDraftDto> mine(@AuthenticationPrincipal User user) {
        if (user == null) throw new IllegalStateException("Unauthorized");
        return drafts.mine(user);
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<JobOfferDetailDto> publish(@AuthenticationPrincipal User user,
                                                     @PathVariable Long id) {
        if (user == null) throw new IllegalStateException("Unauthorized");
        JobOfferDetailDto dto = drafts.publish(user, id, platformBaseUrl);
        return ResponseEntity.created(URI.create("/api/jobs/" + dto.id())).body(dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        if (user == null) throw new IllegalStateException("Unauthorized");
        drafts.delete(user, id);
    }
}
