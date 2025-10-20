package com.milosz.podsiadly.backend.salarycalculator.config;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class SalaryRulesProvider {
    private final int defaultYear = 2025;

    public int defaultYear() { return defaultYear; }

    public BigDecimal pensionPct(int y){ return bd("0.0976"); }
    public BigDecimal disabilityPct(int y){ return bd("0.015"); }
    public BigDecimal sicknessPct(int y){ return bd("0.0245"); }
    public BigDecimal healthPct(int y){ return bd("0.09"); }
    public BigDecimal pitPct(int y){ return bd("0.12"); }

    public boolean uodHasZUS(int y){ return false; }

    public BigDecimal b2bPension(int y){ return bd("812.23"); }
    public BigDecimal b2bDisability(int y){ return bd("332.88"); }
    public BigDecimal b2bAccident(int y){ return bd("69.49"); }
    public BigDecimal b2bFP(int y){ return bd("0.00"); }
    public BigDecimal b2bFGSP(int y){ return bd("0.00"); }
    public BigDecimal b2bHealthAmount(int y){ return bd("381.78"); }
    public BigDecimal b2bLinearPct(int y){ return bd("0.19"); }

    private static BigDecimal bd(String v){ return new BigDecimal(v); }
}
