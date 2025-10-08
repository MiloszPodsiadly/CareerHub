package com.milosz.podsiadly.backend.job.repository;

import com.milosz.podsiadly.backend.job.domain.JobOffer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface JobOfferRepository
        extends JpaRepository<JobOffer, Long>, JpaSpecificationExecutor<JobOffer> {

    boolean existsBySourceAndExternalId(String source, String externalId);
    Optional<JobOffer> findBySourceAndExternalId(String source, String externalId);

    @EntityGraph(attributePaths = {"company", "city"})
    Page<JobOffer> findAll(Specification<JobOffer> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"company", "city", "techTags"})
    Optional<JobOffer> findById(Long id);
}
