package com.milosz.podsiadly.backend.job.repository;

import com.milosz.podsiadly.backend.job.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByNameIgnoreCase(String name);
}