package com.milosz.podsiadly.backend.salarycalculator.domain.calc;

import com.milosz.podsiadly.backend.salarycalculator.domain.model.CalculationInput;
import com.milosz.podsiadly.backend.salarycalculator.domain.model.CalculationResult;

public interface ContractCalculator {
    boolean supports(CalculationInput in);
    CalculationResult calculate(CalculationInput in);
}
