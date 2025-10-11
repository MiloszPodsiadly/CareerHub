package com.milosz.podsiadly.backend.job.repository;

import com.milosz.podsiadly.backend.job.domain.JobOfferHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobOfferHistoryRepository extends JpaRepository<JobOfferHistory, Long> {}