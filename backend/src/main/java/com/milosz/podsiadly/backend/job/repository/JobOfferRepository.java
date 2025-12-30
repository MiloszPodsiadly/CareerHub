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

    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD,
            attributePaths = {"company", "city", "techTags"})
    Optional<JobOffer> findBySourceAndExternalId(JobSource source, String externalId);

    @EntityGraph(attributePaths = {"company", "city"})
    Page<JobOffer> findAll(Specification<JobOffer> spec, Pageable pageable);

    @Query("""
    select distinct o
    from JobOffer o
    left join fetch o.company
    left join fetch o.city
    left join fetch o.contracts
    left join fetch o.techTags
    where o.id in :ids
    """)
    List<JobOffer> findAllHydratedByIdIn(@Param("ids") List<Long> ids);

    Optional<JobOffer> findFirstBySourceAndUrl(JobSource source, String url);


    @EntityGraph(attributePaths = {"company", "city", "techTags"})
    Optional<JobOffer> findById(Long id);

    List<JobOffer> findAllByActiveFalseAndLastSeenAtBefore(Instant olderThan);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
    update JobOffer o
       set o.active = false
     where o.source = :source
       and o.active = true
       and o.lastSeenAt < :cutoff
    """)
    int deactivateStale(@Param("source") JobSource source,
                        @Param("cutoff") Instant cutoff);

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

