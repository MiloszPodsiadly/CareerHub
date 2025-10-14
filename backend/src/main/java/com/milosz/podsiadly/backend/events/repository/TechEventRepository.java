package com.milosz.podsiadly.backend.events.repository;

import com.milosz.podsiadly.backend.events.domain.TechEvent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TechEventRepository extends
        JpaRepository<TechEvent, Long>,
        JpaSpecificationExecutor<TechEvent> {

    Optional<TechEvent> findBySourceAndExternalId(String source, String externalId);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO dev_event (
            source, external_id, url, title, description,
            country, region, city, timezone, online, type,
            start_at, end_at, status, venue, latitude, longitude,
            first_seen_at, last_seen_at, raw, fingerprint
        ) VALUES (
            :source, :externalId, :url, :title, :description,
            :country, :region, :city, :timezone, :online, :type,
            :startAt, :endAt, :status, :venue, :lat, :lon,
            COALESCE((SELECT first_seen_at FROM dev_event WHERE source=:source AND external_id=:externalId), :now),
            :now, :raw, :fingerprint
        )
        ON CONFLICT (source, external_id) DO UPDATE SET
            url          = EXCLUDED.url,
            title        = EXCLUDED.title,
            description  = EXCLUDED.description,
            country      = EXCLUDED.country,
            region       = EXCLUDED.region,
            city         = EXCLUDED.city,
            timezone     = EXCLUDED.timezone,
            online       = EXCLUDED.online,
            type         = EXCLUDED.type,
            start_at     = EXCLUDED.start_at,
            end_at       = EXCLUDED.end_at,
            status       = EXCLUDED.status,
            venue        = EXCLUDED.venue,
            latitude     = EXCLUDED.latitude,
            longitude    = EXCLUDED.longitude,
            last_seen_at = EXCLUDED.last_seen_at,
            raw          = EXCLUDED.raw,
            fingerprint  = EXCLUDED.fingerprint
        """, nativeQuery = true)
    int upsert(
            @Param("source") String source,
            @Param("externalId") String externalId,
            @Param("url") String url,
            @Param("title") String title,
            @Param("description") String description,
            @Param("country") String country,
            @Param("region") String region,
            @Param("city") String city,
            @Param("timezone") String timezone,
            @Param("online") Boolean online,
            @Param("type") String type,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("status") String status,
            @Param("venue") String venue,
            @Param("lat") Double lat,
            @Param("lon") Double lon,
            @Param("now") Instant now,
            @Param("raw") String raw,
            @Param("fingerprint") String fingerprint
    );

    @Query("select e.id from TechEvent e where e.source = :source and e.externalId = :externalId")
    Long findId(@Param("source") String source, @Param("externalId") String externalId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM dev_event_tags WHERE event_id = :id", nativeQuery = true)
    void deleteTags(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO dev_event_tags(event_id, tag) VALUES (:id, :tag)", nativeQuery = true)
    void insertTag(@Param("id") Long id, @Param("tag") String tag);

    @Query("""
           select e.country, count(e)
           from TechEvent e
           where e.country is not null
           group by e.country
           order by count(e) desc
           """)
    List<Object[]> countryCounts();

    @Query("""
           select e.city, count(e)
           from TechEvent e
           where e.country = :country and e.city is not null
           group by e.city
           order by count(e) desc
           """)
    List<Object[]> cityCounts(@Param("country") String country);
}
