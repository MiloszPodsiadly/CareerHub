package com.milosz.podsiadly.backend.salarycalculator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.milosz.podsiadly.backend.salarycalculator.domain.AmountMode;
import com.milosz.podsiadly.backend.salarycalculator.domain.ContractType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SalaryRequest(
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        BigDecimal amount,
        @NotNull(message = "Amount mode is required")
        AmountMode amountMode,
        @NotNull(message = "Contract type is required")
        ContractType contractType,
        @NotNull(message = "Year is required")
        Integer year,
        Boolean pit0
) {}
