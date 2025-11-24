package com.milosz.podsiadly.backend.job.controller;

import com.milosz.podsiadly.backend.job.dto.JobOfferHistoryDto;
import com.milosz.podsiadly.backend.job.mapper.JobOfferHistoryMapper;
import com.milosz.podsiadly.backend.job.repository.JobOfferHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/jobs/history")
@RequiredArgsConstructor
public class JobOfferHistoryController {

    private final JobOfferHistoryRepository repo;
    private final JobOfferHistoryMapper mapper;

    @GetMapping
    public Page<JobOfferHistoryDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page), Math.min(size, 200),
                Sort.by(Sort.Direction.DESC, "archivedAt")
        );
        return repo.findAll(pageable).map(mapper::toDto);
    }

    @GetMapping("/{id}")
    public JobOfferHistoryDto get(@PathVariable Long id) {
        var h = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return mapper.toDto(h);
    }
}