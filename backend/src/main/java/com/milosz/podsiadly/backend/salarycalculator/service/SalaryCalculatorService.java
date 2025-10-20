package com.milosz.podsiadly.backend.salarycalculator.service;

import com.milosz.podsiadly.backend.salarycalculator.config.SalaryRulesProvider;
import com.milosz.podsiadly.backend.salarycalculator.domain.AmountMode;
import com.milosz.podsiadly.backend.salarycalculator.domain.calc.ContractCalculator;
import com.milosz.podsiadly.backend.salarycalculator.domain.model.CalculationInput;
import com.milosz.podsiadly.backend.salarycalculator.domain.model.CalculationResult;
import com.milosz.podsiadly.backend.salarycalculator.dto.SalaryRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SalaryCalculatorService {

    private final List<ContractCalculator> calculators;
    private final SalaryRulesProvider rules;

    public SalaryCalculatorService(List<ContractCalculator> calculators, SalaryRulesProvider rules){
        this.calculators = calculators;
        this.rules = rules;
    }

    public CalculationInput toInput(SalaryRequest req){
        int year = req.year()!=null ? req.year() : rules.defaultYear();
        return new CalculationInput(
                req.amount(),
                req.amountMode()!=null ? req.amountMode() : AmountMode.GROSS,
                req.contractType(),
                year,
                Boolean.TRUE.equals(req.pit0())
        );
    }

    public CalculationResult calculate(SalaryRequest req){
        var in = toInput(req);
        var calc = calculators.stream()
                .filter(c -> c.supports(in))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported contract type: "+in.contractType()));

        if (in.amountMode() == AmountMode.GROSS) {
            return calc.calculate(in);
        }
        return com.milosz.podsiadly.backend.salarycalculator.domain.model.NetGrossSolver.solve(calc, in);
    }
}
