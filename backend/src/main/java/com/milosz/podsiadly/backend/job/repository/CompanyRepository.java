package com.milosz.podsiadly.backend.job.repository;

import com.milosz.podsiadly.backend.job.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findFirstByNameIgnoreCaseOrderByIdAsc(String name);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
    INSERT INTO company(name)
    VALUES (:name)
    ON CONFLICT (name) DO NOTHING
    """, nativeQuery = true)
    int insertIgnore(@Param("name") String name);

}
