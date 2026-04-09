package com.milosz.podsiadly.backend.unit.job.service;

import com.milosz.podsiadly.backend.job.domain.SalaryPeriod;
import com.milosz.podsiadly.backend.job.service.SalaryNormalizer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class SalaryNormalizerTests {

    @Test
    void should_normalize_hourly_range_to_monthly_values() {
        SalaryNormalizer.Normalized result = SalaryNormalizer.normalizeToMonth(100, 150, SalaryPeriod.HOUR);

        assertThat(result.monthMin()).isEqualTo(16800);
        assertThat(result.monthMax()).isEqualTo(25200);
    }

    @Test
    void should_swap_values_when_normalized_min_is_greater_than_max() {
        SalaryNormalizer.Normalized result = SalaryNormalizer.normalizeToMonth(12_000, 6_000, SalaryPeriod.MONTH);

        assertThat(result.monthMin()).isEqualTo(6_000);
        assertThat(result.monthMax()).isEqualTo(12_000);
    }

    @Test
    void should_return_nulls_when_both_values_are_missing() {
        SalaryNormalizer.Normalized result = SalaryNormalizer.normalizeToMonth(null, null, SalaryPeriod.MONTH);

        assertThat(result.monthMin()).isNull();
        assertThat(result.monthMax()).isNull();
    }

    @Test
    void should_parse_period_from_loose_human_text() {
        assertThat(SalaryNormalizer.parsePeriodLoose("120 pln / godz")).isEqualTo(SalaryPeriod.HOUR);
        assertThat(SalaryNormalizer.parsePeriodLoose("per year")).isEqualTo(SalaryPeriod.YEAR);
        assertThat(SalaryNormalizer.parsePeriodLoose("miesiecznie")).isEqualTo(SalaryPeriod.MONTH);
    }
}
