package com.milosz.podsiadly.backend.job.repository;

import com.milosz.podsiadly.backend.job.domain.City;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CityRepository extends JpaRepository<City, Long> {
    Optional<City> findFirstByNameIgnoreCaseOrderByIdAsc(String name);
}
