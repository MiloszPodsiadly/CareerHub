package com.milosz.podsiadly.backend.salarycalculator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.milosz.podsiadly.backend.salarycalculator.domain.AmountMode;
import com.milosz.podsiadly.backend.salarycalculator.domain.ContractType;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SalaryRequest(
        BigDecimal amount,
        AmountMode amountMode,
        ContractType contractType,
        Integer year,
        Boolean pit0
) {}
