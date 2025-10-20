package com.milosz.podsiadly.backend.salarycalculator.domain.calc;

import com.milosz.podsiadly.backend.salarycalculator.config.SalaryRulesProvider;
import com.milosz.podsiadly.backend.salarycalculator.domain.AmountMode;
import com.milosz.podsiadly.backend.salarycalculator.domain.ContractType;
import com.milosz.podsiadly.backend.salarycalculator.domain.model.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.milosz.podsiadly.backend.salarycalculator.domain.model.MathUtil.*;

public class B2BCalculator implements ContractCalculator {

    private final SalaryRulesProvider rules;
    public B2BCalculator(SalaryRulesProvider rules){ this.rules = rules; }

    @Override public boolean supports(CalculationInput in){ return in.contractType()== ContractType.B2B; }

    @Override
    public CalculationResult calculate(CalculationInput in) {
        if(in.amountMode()!= AmountMode.GROSS) throw new UnsupportedOperationException("NET→GROSS handled externally");
        int y = in.year();

        var revenue = round2(in.amount());
        var pension = rules.b2bPension(y);
        var disability = rules.b2bDisability(y);
        var sickness = BigDecimal.ZERO;
        var accident = rules.b2bAccident(y);
        var fp = rules.b2bFP(y);
        var fgsp = rules.b2bFGSP(y);
        var social = pension.add(disability).add(sickness).add(accident).add(fp).add(fgsp);

        var health = rules.b2bHealthAmount(y);
        var pitBase = round2(revenue.subtract(social).max(BigDecimal.ZERO));
        var pit = in.pit0()? BigDecimal.ZERO : pct(pitBase, rules.b2bLinearPct(y));

        var net = round2(revenue.subtract(social).subtract(health).subtract(pit));
        var yearlyNet = round2(net.multiply(BigDecimal.valueOf(12)));

        var proportions = Map.of(
                "net", safeDiv(net, revenue),
                "pit", safeDiv(pit, revenue),
                "health", safeDiv(health, revenue)
        );

        var months = List.of("January","February","March","April","May","June","July","August","September","October","November","December");
        var monthly = months.stream().map(m ->
                new CalculationResult.MonthlyRow(m, revenue, social.subtract(health), health, pit, net)
        ).toList();

        return new CalculationResult(
                revenue, net, yearlyNet,
                new Deductions(pension, disability, sickness, health, pit),
                Map.of("pitBase", pitBase),
                proportions,
                monthly
        );
    }

    private static BigDecimal safeDiv(BigDecimal a, BigDecimal b){
        if(b.signum()==0) return BigDecimal.ZERO;
        return a.divide(b, 4, java.math.RoundingMode.HALF_UP);
    }
}
