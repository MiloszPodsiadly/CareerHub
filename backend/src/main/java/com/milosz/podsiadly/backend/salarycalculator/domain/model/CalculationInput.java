package com.milosz.podsiadly.backend.salarycalculator.domain.model;

import com.milosz.podsiadly.backend.salarycalculator.domain.AmountMode;
import com.milosz.podsiadly.backend.salarycalculator.domain.ContractType;

import java.math.BigDecimal;

public record CalculationInput(
        BigDecimal amount,
        AmountMode amountMode,
        ContractType contractType,
        int year,
        boolean pit0
) {}
