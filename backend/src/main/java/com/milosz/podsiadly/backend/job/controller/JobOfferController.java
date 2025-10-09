// src/main/java/com/milosz/podsiadly/backend/job/controller/JobOfferController.java
package com.milosz.podsiadly.backend.job.controller;

import com.milosz.podsiadly.backend.job.domain.ContractType;
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
            @RequestParam(required = false, name = "seniority") JobLevel seniorityAlias,
            @RequestParam(required = false) List<String> spec,
            @RequestParam(required = false) List<String> tech,
            @RequestParam(required = false) Integer salaryMin,
            @RequestParam(required = false) Integer salaryMax,
            @RequestParam(required = false) ContractType contract,
            @RequestParam(required = false) Boolean withSalary,
            @RequestParam(required = false) Instant postedAfter,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "relevance") String sort
    ) {
        JobLevel effectiveLevel = (level != null) ? level : seniorityAlias;

        int idx = Math.max(0, page - 1);
        int effSize = (pageSize != null && pageSize > 0) ? pageSize : size;

        Sort s = switch (sort.toLowerCase()) {
            case "salary" -> Sort.by(Sort.Direction.DESC, "salaryMax").and(Sort.by("salaryMin").descending());
            case "date" -> Sort.by(Sort.Direction.DESC, "publishedAt");
            default -> Sort.by(Sort.Direction.DESC, "publishedAt");
        };

        Pageable pageable = PageRequest.of(idx, effSize, s);

        return service.search(q, city, remote, effectiveLevel,
                spec,
                tech,
                salaryMin, salaryMax, postedAfter,
                contract, withSalary, pageable);
    }

    @GetMapping("/all")
    public List<JobOfferListDto> searchAll(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Boolean remote,
            @RequestParam(required = false) JobLevel level,
            @RequestParam(required = false, name = "seniority") JobLevel seniorityAlias,
            @RequestParam(required = false) List<String> spec,
            @RequestParam(required = false) List<String> tech,
            @RequestParam(required = false) Integer salaryMin,
            @RequestParam(required = false) Integer salaryMax,
            @RequestParam(required = false) ContractType contract,
            @RequestParam(required = false) Boolean withSalary,
            @RequestParam(required = false) Instant postedAfter,
            @RequestParam(defaultValue = "date") String sort
    ) {
        JobLevel effectiveLevel = (level != null) ? level : seniorityAlias;

        Sort s = "salary".equalsIgnoreCase(sort)
                ? Sort.by(Sort.Direction.DESC, "salaryMax").and(Sort.by("salaryMin").descending())
                : Sort.by(Sort.Direction.DESC, "publishedAt");

        return service.searchAll(q, city, remote, effectiveLevel,
                spec,
                tech,
                salaryMin, salaryMax, postedAfter,
                contract, withSalary, s);
    }
    @GetMapping("/{id}")
    public JobOfferDetailDto get(@PathVariable Long id) {
        return service.get(id);
    }
}