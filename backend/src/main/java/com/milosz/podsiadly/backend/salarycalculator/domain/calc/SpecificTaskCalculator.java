package com.milosz.podsiadly.backend.salarycalculator.domain.calc;

import com.milosz.podsiadly.backend.salarycalculator.config.SalaryRulesProvider;
import com.milosz.podsiadly.backend.salarycalculator.domain.AmountMode;
import com.milosz.podsiadly.backend.salarycalculator.domain.ContractType;
import com.milosz.podsiadly.backend.salarycalculator.domain.model.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.milosz.podsiadly.backend.salarycalculator.domain.model.MathUtil.*;

public class SpecificTaskCalculator implements ContractCalculator {

    private final SalaryRulesProvider rules;
    public SpecificTaskCalculator(SalaryRulesProvider rules){ this.rules = rules; }

    @Override public boolean supports(CalculationInput in){ return in.contractType()== ContractType.SPECIFIC_TASK; }

    @Override
    public CalculationResult calculate(CalculationInput in) {
        if(in.amountMode()!= AmountMode.GROSS) throw new UnsupportedOperationException("NET→GROSS handled externally");
        int y = in.year();

        var gross = round2(in.amount());
        var pension = BigDecimal.ZERO;
        var disability = BigDecimal.ZERO;
        var sickness = BigDecimal.ZERO;
        var health = BigDecimal.ZERO;

        var pit = in.pit0() ? BigDecimal.ZERO : pct(gross, rules.pitPct(y));

        var net = round2(gross.subtract(pit));
        var yearlyNet = round2(net.multiply(BigDecimal.valueOf(12)));

        var proportions = Map.of(
                "net", safeDiv(net, gross),
                "pit", safeDiv(pit, gross)
        );
        var months = List.of("January","February","March","April","May","June","July","August","September","October","November","December");
        var monthly = months.stream().map(m -> new CalculationResult.MonthlyRow(m, gross, BigDecimal.ZERO, health, pit, net)).toList();

        return new CalculationResult(
                gross, net, yearlyNet,
                new Deductions(pension, disability, sickness, health, pit),
                Map.of("taxBase", gross),
                proportions,
                monthly
        );
    }

    private static BigDecimal safeDiv(BigDecimal a, BigDecimal b){
        if(b.signum()==0) return BigDecimal.ZERO;
        return a.divide(b, 4, java.math.RoundingMode.HALF_UP);
    }
}
