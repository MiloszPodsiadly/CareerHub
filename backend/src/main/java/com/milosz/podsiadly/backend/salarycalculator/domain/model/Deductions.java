package com.milosz.podsiadly.backend.salarycalculator.domain.model;

import java.math.BigDecimal;

public record Deductions(
        BigDecimal pension,
        BigDecimal disability,
        BigDecimal sickness,
        BigDecimal health,
        BigDecimal pit
) {}
