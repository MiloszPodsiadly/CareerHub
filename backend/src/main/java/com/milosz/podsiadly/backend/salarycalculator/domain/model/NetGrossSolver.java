package com.milosz.podsiadly.backend.salarycalculator.domain.model;

import com.milosz.podsiadly.backend.salarycalculator.domain.AmountMode;
import com.milosz.podsiadly.backend.salarycalculator.domain.calc.ContractCalculator;

import java.math.BigDecimal;

import static com.milosz.podsiadly.backend.salarycalculator.domain.model.MathUtil.round2;

public final class NetGrossSolver {
    private NetGrossSolver(){}

    public static CalculationResult solve(ContractCalculator calc, CalculationInput in){
        BigDecimal targetNet = in.amount();
        double lo = 0.0, hi = Math.max(5000.0, targetNet.doubleValue()*1.8 + 2000.0);

        for(int i=0;i<40;i++){
            double mid = (lo+hi)/2.0;
            var tryIn = new CalculationInput(
                    round2(BigDecimal.valueOf(mid)),
                    AmountMode.GROSS, in.contractType(), in.year(), in.pit0()
            );
            var r = calc.calculate(tryIn);
            double net = r.net().doubleValue();
            if(net < targetNet.doubleValue()) lo = mid; else hi = mid;
        }
        var finalIn = new CalculationInput(
                round2(BigDecimal.valueOf(hi)),
                AmountMode.GROSS, in.contractType(), in.year(), in.pit0()
        );
        return calc.calculate(finalIn);
    }
}
