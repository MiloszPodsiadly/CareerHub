package com.milosz.podsiadly.backend.domain.myapplication;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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
      left join a.offer o
      left join com.milosz.podsiadly.backend.job.domain.JobOfferOwner ow on ow.jobOffer = o
      where (o is not null and ow.user.id = :ownerId)
         or (o is null and a.offerOwnerIdSnapshot = :ownerId)
      order by a.createdAt desc
    """)
    List<JobApplication> findForOwnedOffers(@Param("ownerId") String ownerId);

    @Query("""
      select a from JobApplication a
      left join a.offer o
      left join com.milosz.podsiadly.backend.job.domain.JobOfferOwner ow on ow.jobOffer = o
      where a.id = :appId
        and (
             a.applicant.id = :userId
             or (o is not null and ow.user.id = :userId)
             or (o is null and a.offerOwnerIdSnapshot = :userId)
        )
    """)
    Optional<JobApplication> findAccessible(@Param("appId") Long appId, @Param("userId") String userId);
}
