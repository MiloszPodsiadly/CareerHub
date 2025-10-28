package com.milosz.podsiadly.backend.job.repository;

import com.milosz.podsiadly.backend.job.domain.JobDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobDraftRepository extends JpaRepository<JobDraft, Long> {
    List<JobDraft> findAllByOwner_IdOrderByUpdatedAtDesc(String ownerId);
    Optional<JobDraft> findTop1ByOwner_IdOrderByUpdatedAtDesc(String ownerId);
}
