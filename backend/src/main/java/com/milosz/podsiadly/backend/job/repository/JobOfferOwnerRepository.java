package com.milosz.podsiadly.backend.job.repository;

import com.milosz.podsiadly.backend.job.domain.JobOfferOwner;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobOfferOwnerRepository extends JpaRepository<JobOfferOwner, Long> {

    @EntityGraph(attributePaths = {
            "jobOffer", "jobOffer.company", "jobOffer.city"
    })

    Optional<JobOfferOwner> findByJobOffer_Id(Long jobOfferId);

    void deleteByJobOffer_Id(Long jobOfferId);
}