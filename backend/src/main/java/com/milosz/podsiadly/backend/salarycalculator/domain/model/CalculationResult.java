package com.milosz.podsiadly.backend.salarycalculator.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record CalculationResult(
        BigDecimal gross,
        BigDecimal net,
        BigDecimal yearlyNet,
        Deductions items,
        Map<String, BigDecimal> details,
        Map<String, BigDecimal> proportions,
        List<MonthlyRow> monthly
) {
    public record MonthlyRow(
            String month,
            BigDecimal gross,
            BigDecimal social,
            BigDecimal health,
            BigDecimal pit,
            BigDecimal net
    ) {}
}
