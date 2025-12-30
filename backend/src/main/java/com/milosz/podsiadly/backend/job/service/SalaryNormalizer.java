package com.milosz.podsiadly.backend.job.service;

import com.milosz.podsiadly.backend.job.domain.SalaryPeriod;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class SalaryNormalizer {
    private SalaryNormalizer() {}

    private static final BigDecimal HOURS_PER_MONTH = bd("168");
    private static final BigDecimal DAYS_PER_MONTH  = bd("21.75");
    private static final BigDecimal WEEKS_PER_MONTH = bd("4.34524");

    public record Normalized(Integer monthMin, Integer monthMax) {}

    public static Normalized normalizeToMonth(Integer min, Integer max, SalaryPeriod period) {
        if (min == null && max == null) return new Normalized(null, null);

        SalaryPeriod p = (period != null) ? period : SalaryPeriod.MONTH;

        Integer nMin = normalizeOne(min, p);
        Integer nMax = normalizeOne(max, p);

        if (nMin != null && nMax != null && nMin > nMax) {
            int tmp = nMin;
            nMin = nMax;
            nMax = tmp;
        }

        return new Normalized(nMin, nMax);
    }

    private static Integer normalizeOne(Integer value, SalaryPeriod p) {
        if (value == null) return null;

        BigDecimal v = BigDecimal.valueOf(value);

        BigDecimal out = switch (p) {
            case MONTH -> v;
            case YEAR  -> v.divide(bd("12"), 0, RoundingMode.HALF_UP);
            case WEEK  -> v.multiply(WEEKS_PER_MONTH).setScale(0, RoundingMode.HALF_UP);
            case DAY   -> v.multiply(DAYS_PER_MONTH).setScale(0, RoundingMode.HALF_UP);
            case HOUR  -> v.multiply(HOURS_PER_MONTH).setScale(0, RoundingMode.HALF_UP);
        };

        if (out.compareTo(bd("0")) <= 0) return null;
        if (out.compareTo(bd("10000000")) > 0) return null;

        return out.intValueExact();
    }

    public static SalaryPeriod parsePeriodLoose(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String t = raw.trim().toLowerCase();

        if (t.contains("hour") || t.contains("godz")) return SalaryPeriod.HOUR;
        if (t.contains("day")  || t.contains("dzien") || t.contains("dzień")) return SalaryPeriod.DAY;
        if (t.contains("week") || t.contains("tyg")) return SalaryPeriod.WEEK;
        if (t.contains("month")|| t.contains("mies")) return SalaryPeriod.MONTH;
        if (t.contains("year") || t.contains("annual") || t.contains("rok") || t.contains("per year")) return SalaryPeriod.YEAR;

        return null;
    }

    private static BigDecimal bd(String x) { return new BigDecimal(x); }
}
