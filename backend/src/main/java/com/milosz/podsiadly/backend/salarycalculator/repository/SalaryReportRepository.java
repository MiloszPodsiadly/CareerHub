package com.milosz.podsiadly.backend.salarycalculator.repository;

import com.milosz.podsiadly.backend.salarycalculator.repository.entity.SalaryReportEntity;
import org.springframework.data.repository.CrudRepository;

public interface SalaryReportRepository extends CrudRepository<SalaryReportEntity, String> {}
