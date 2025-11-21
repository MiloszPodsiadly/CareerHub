package com.milosz.podsiadly.backend.job.repository;

import com.milosz.podsiadly.backend.job.domain.JobOffer;
import com.milosz.podsiadly.backend.job.domain.JobSource;
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

    boolean existsBySourceAndExternalId(JobSource source, String externalId);
    Optional<JobOffer> findBySourceAndExternalId(JobSource source, String externalId);

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
      where u.id = :userId and j.source = :source
      order by j.publishedAt desc, j.id desc
    """)
    List<JobOffer> findOwnedByUserIdAndSource(@Param("userId") String userId,
                                              @Param("source") JobSource source);
}