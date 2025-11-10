package com.milosz.podsiadly.backend.domain.myapplication;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    List<JobApplication> findByApplicant_IdOrderByCreatedAtDesc(String userId);

    boolean existsByApplicant_IdAndOffer_Id(String userId, Long offerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update JobApplication a set a.offer = null where a.offer.id = :offerId")
    int detachOfferById(@Param("offerId") Long offerId);

    @Query("""
      select a from JobApplication a
      join a.offer o
      join com.milosz.podsiadly.backend.job.domain.JobOfferOwner ow on ow.jobOffer = o
      where ow.user.id = ?1
      order by a.createdAt desc
    """)
    List<JobApplication> findForOwnedOffers(String ownerId);

    @Query("""
      select a from JobApplication a
      join a.offer o
      join com.milosz.podsiadly.backend.job.domain.JobOfferOwner ow on ow.jobOffer = o
      where a.id = ?1 and (a.applicant.id = ?2 or ow.user.id = ?2)
    """)
    Optional<JobApplication> findAccessible(Long appId, String userId);
}
