// src/main/java/com/milosz/podsiadly/backend/job/controller/JobOfferController.java
package com.milosz.podsiadly.backend.job.controller;

import com.milosz.podsiadly.backend.job.domain.JobLevel;
import com.milosz.podsiadly.backend.job.dto.JobOfferDetailDto;
import com.milosz.podsiadly.backend.job.dto.JobOfferListDto;
import com.milosz.podsiadly.backend.job.service.JobOfferService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@CrossOrigin
public class JobOfferController {

    private final JobOfferService service;

    @GetMapping
    public Page<JobOfferListDto> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Boolean remote,
            @RequestParam(required = false) JobLevel level,
            @RequestParam(required = false) List<String> tech,
            @RequestParam(required = false) Integer salaryMin,
            @RequestParam(required = false) Integer salaryMax,
            @RequestParam(required = false) Instant postedAfter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "publishedAt,desc") String sort
    ) {
        Sort s = sort.contains(",")
                ? Sort.by(new Sort.Order(
                sort.toLowerCase().endsWith("desc") ? Sort.Direction.DESC : Sort.Direction.ASC,
                sort.split(",")[0]
        ))
                : Sort.by(Sort.Direction.DESC, "publishedAt");

        Pageable pageable = PageRequest.of(page, size, s);
        return service.search(q, city, remote, level, tech, salaryMin, salaryMax, postedAfter, pageable);
    }

    @GetMapping("/{id}")
    public JobOfferDetailDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/all")
    public List<JobOfferListDto> searchAll(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Boolean remote,
            @RequestParam(required = false) JobLevel level,
            @RequestParam(required = false) List<String> tech,
            @RequestParam(required = false) Integer salaryMin,
            @RequestParam(required = false) Integer salaryMax,
            @RequestParam(required = false) Instant postedAfter,
            @RequestParam(defaultValue = "publishedAt,desc") String sort
    ) {
        Sort s = sort.contains(",")
                ? Sort.by(new Sort.Order(
                sort.toLowerCase().endsWith("desc") ? Sort.Direction.DESC : Sort.Direction.ASC,
                sort.split(",")[0]
        ))
                : Sort.by(Sort.Direction.DESC, "publishedAt");

        return service.searchAll(q, city, remote, level, tech, salaryMin, salaryMax, postedAfter, s);
    }
}
