package com.milosz.podsiadly.backend.salarycalculator.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MathUtil {
    private MathUtil(){}

    public static BigDecimal round2(BigDecimal v){
        if(v==null) return BigDecimal.ZERO;
        return v.setScale(2, RoundingMode.HALF_UP);
    }
    public static BigDecimal pct(BigDecimal base, BigDecimal rate){
        if(base==null || rate==null) return BigDecimal.ZERO;
        return round2(base.multiply(rate));
    }
}
