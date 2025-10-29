package com.milosz.podsiadly.backend.job.repository;

import com.milosz.podsiadly.backend.job.domain.JobOfferOwner;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;

public interface JobOfferOwnerRepository extends JpaRepository<JobOfferOwner, Long> {

    @EntityGraph(attributePaths = {
            "jobOffer", "jobOffer.company", "jobOffer.city"
    })

    Optional<JobOfferOwner> findByJobOffer_Id(Long jobOfferId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    void deleteByJobOffer_Id(Long jobOfferId);

    int countByJobOffer_Id(Long jobOfferId);
}