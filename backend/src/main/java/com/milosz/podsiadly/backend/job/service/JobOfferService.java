package com.milosz.podsiadly.backend.job.service;

import com.milosz.podsiadly.backend.job.domain.JobLevel;
import com.milosz.podsiadly.backend.job.domain.JobOffer;
import com.milosz.podsiadly.backend.job.dto.JobOfferDetailDto;
import com.milosz.podsiadly.backend.job.dto.JobOfferListDto;
import com.milosz.podsiadly.backend.job.mapper.JobOfferMapper;
import com.milosz.podsiadly.backend.job.repository.JobOfferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JobOfferService {

    private final JobOfferRepository repo;

    @Transactional(readOnly = true)
    public Page<JobOfferListDto> search(
            String q, String city, Boolean remote, JobLevel level, List<String> tech,
            Integer salaryMin, Integer salaryMax, Instant postedAfter, Pageable pageable
    ) {
        Specification<JobOffer> spec = Specification.allOf(
                JobOfferSpecifications.active(),
                JobOfferSpecifications.text(q),
                JobOfferSpecifications.byCity(city),
                JobOfferSpecifications.remote(remote),
                JobOfferSpecifications.level(level),
                JobOfferSpecifications.techAny(tech),
                JobOfferSpecifications.salaryBetween(salaryMin, salaryMax),
                JobOfferSpecifications.postedAfter(postedAfter)
        );

        return repo.findAll(spec, pageable)
                .map(JobOfferMapper::toListDto);
    }

    @Transactional(readOnly = true)
    public JobOfferDetailDto get(Long id) {
        return repo.findById(id)
                .map(JobOfferMapper::toDetailDto)
                .orElseThrow(() -> new IllegalArgumentException("Offer not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<JobOfferListDto> searchAll(
            String q, String city, Boolean remote, JobLevel level, List<String> tech,
            Integer salaryMin, Integer salaryMax, Instant postedAfter, Sort sort
    ) {
        Specification<JobOffer> spec = Specification.allOf(
                JobOfferSpecifications.active(),
                JobOfferSpecifications.text(q),
                JobOfferSpecifications.byCity(city),
                JobOfferSpecifications.remote(remote),
                JobOfferSpecifications.level(level),
                JobOfferSpecifications.techAny(tech),
                JobOfferSpecifications.salaryBetween(salaryMin, salaryMax),
                JobOfferSpecifications.postedAfter(postedAfter)
        );

        return repo.findAll(spec, sort)
                .stream()
                .map(JobOfferMapper::toListDto)
                .toList();
    }

}
