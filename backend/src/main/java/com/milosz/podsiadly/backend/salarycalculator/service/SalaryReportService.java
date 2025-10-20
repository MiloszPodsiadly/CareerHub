package com.milosz.podsiadly.backend.salarycalculator.service;

import com.milosz.podsiadly.backend.salarycalculator.domain.model.CalculationInput;
import com.milosz.podsiadly.backend.salarycalculator.domain.model.CalculationResult;
import com.milosz.podsiadly.backend.salarycalculator.dto.SalaryResponse;
import com.milosz.podsiadly.backend.salarycalculator.repository.SalaryReportEntityMapper;
import com.milosz.podsiadly.backend.salarycalculator.repository.SalaryReportRepository;
import com.milosz.podsiadly.backend.salarycalculator.repository.entity.SalaryReportEntity;
import org.springframework.stereotype.Service;

@Service
public class SalaryReportService {

    private final SalaryReportRepository repo;

    public SalaryReportService(SalaryReportRepository repo){ this.repo = repo; }

    public SalaryReportEntity persist(CalculationInput in, CalculationResult result, Long ttlSeconds){
        var entity = SalaryReportEntityMapper.toEntity(in, result, ttlSeconds);
        return repo.save(entity);
    }

    public SalaryResponse get(String id){
        return repo.findById(id).map(SalaryReportEntityMapper::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: "+id));
    }
}
