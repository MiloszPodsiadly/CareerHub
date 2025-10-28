package com.milosz.podsiadly.backend.job.repository;

import com.milosz.podsiadly.backend.job.domain.JobOffer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface JobOfferRepository
        extends JpaRepository<JobOffer, Long>, JpaSpecificationExecutor<JobOffer> {

    boolean existsBySourceAndExternalId(String source, String externalId);
    Optional<JobOffer> findBySourceAndExternalId(String source, String externalId);

    @EntityGraph(attributePaths = {"company", "city"})
    Page<JobOffer> findAll(Specification<JobOffer> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"company", "city", "techTags"})
    Optional<JobOffer> findById(Long id);

    List<JobOffer> findAllByActiveFalseAndLastSeenAtBefore(Instant olderThan);

    @Query("""
      select j
      from JobOffer j
      join j.owner o
      join o.user u
      left join fetch j.company
      left join fetch j.city
      where u.id = :userId
      order by j.publishedAt desc, j.id desc
    """)
    List<JobOffer> findOwnedByUserId(@Param("userId") String userId);

    @Query("""
      select j
      from JobOffer j
      join j.owner o
      join o.user u
      left join fetch j.company
      left join fetch j.city
      where u.id = :userId and j.source = 'platform'
      order by j.publishedAt desc, j.id desc
    """)
    List<JobOffer> findPlatformOwnedByUserId(@Param("userId") String userId);
}
