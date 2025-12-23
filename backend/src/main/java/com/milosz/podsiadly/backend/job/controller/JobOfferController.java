package com.milosz.podsiadly.backend.job.controller;

import com.milosz.podsiadly.backend.domain.loginandregister.User;
import com.milosz.podsiadly.backend.job.domain.ContractType;
import com.milosz.podsiadly.backend.job.domain.JobLevel;
import com.milosz.podsiadly.backend.job.domain.JobSource;
import com.milosz.podsiadly.backend.job.dto.*;
import com.milosz.podsiadly.backend.job.service.JobOfferCommandService;
import com.milosz.podsiadly.backend.job.service.JobOfferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobOfferController {

    private final JobOfferService service;
    private final JobOfferCommandService commands;

    @Value("${app.platform.base-url:http://localhost:3000}")
    private String platformBaseUrl;

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
            @RequestParam(required = false, name = "contract") List<ContractType> contracts,
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
            case "salary" -> Sort.unsorted();
            case "date" -> Sort.by(Sort.Direction.DESC, "publishedAt")
                    .and(Sort.by(Sort.Direction.DESC, "id"));
            default -> Sort.by(Sort.Direction.DESC, "publishedAt")
                    .and(Sort.by(Sort.Direction.DESC, "id"));
        };

        Pageable pageable = PageRequest.of(idx, effSize, s);

        return service.search(
                q, city, remote, effectiveLevel,
                spec, tech,
                salaryMin, salaryMax, postedAfter,
                contracts != null ? Set.copyOf(contracts) : Set.of(),
                withSalary, pageable,
                sort
        );
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
            @RequestParam(required = false, name = "contract") List<ContractType> contracts,
            @RequestParam(required = false) Boolean withSalary,
            @RequestParam(required = false) Instant postedAfter,
            @RequestParam(defaultValue = "date") String sort
    ) {
        JobLevel effectiveLevel = (level != null) ? level : seniorityAlias;

        Sort s = "salary".equalsIgnoreCase(sort)
                ? Sort.unsorted()
                : Sort.by(Sort.Direction.DESC, "publishedAt")
                .and(Sort.by(Sort.Direction.DESC, "id"));

        return service.searchAll(
                q, city, remote, effectiveLevel,
                spec, tech,
                salaryMin, salaryMax, postedAfter,
                contracts != null ? Set.copyOf(contracts) : Set.of(),
                withSalary, s,
                sort
        );

    }

    @GetMapping("/by-external/{externalId}")
    public JobOfferDetailDto getByExternal(
            @PathVariable String externalId,
            @RequestParam(defaultValue = "PLATFORM") JobSource source
    ) {
        return service.getByExternalId(source, externalId);
    }

    @GetMapping("/{id:\\d+}")
    public JobOfferDetailDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public JobOfferDetailDto create(
            @AuthenticationPrincipal User user,
            @RequestBody JobOfferCreateRequest req
    ) {
        if (user == null) throw new IllegalStateException("Must be authenticated to publish a job.");
        return commands.create(user, req, platformBaseUrl);
    }

    @PutMapping("/{id:\\d+}")
    public JobOfferDetailDto update(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody JobOfferUpdateRequest req
    ) {
        if (user == null) throw new IllegalStateException("Unauthorized.");
        return commands.updateOwned(user, id, req);
    }

    @DeleteMapping("/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestHeader(value = "X-Account-Password", required = false) String pwdHeader,
            @RequestParam(value = "password", required = false) String pwdQuery,
            @RequestBody(required = false) DeleteWithPasswordRequest req
    ) {
        if (user == null) throw new IllegalStateException("Unauthorized.");

        final String pwd = pwdHeader != null ? pwdHeader
                : (pwdQuery != null ? pwdQuery
                : (req != null ? req.password() : null));

        log.info("[jobs.delete] user={} offer={} hasHeader={} hasQuery={} hasBody={}",
                user.getUsername(), id, pwdHeader != null, pwdQuery != null, req != null);

        commands.deleteOwned(user, id, pwd);
    }

    @GetMapping("/mine")
    public List<JobOfferListDto> mine(@AuthenticationPrincipal User user) {
        if (user == null) throw new IllegalStateException("Unauthorized.");
        var out = service.listOwned(user.getId());
        log.info("[jobs.mine] userId={} count={}", user.getId(), out.size());
        return out;
    }
}
