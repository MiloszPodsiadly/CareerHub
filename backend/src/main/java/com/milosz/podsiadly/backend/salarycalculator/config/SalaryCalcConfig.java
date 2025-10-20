package com.milosz.podsiadly.backend.salarycalculator.config;

import com.milosz.podsiadly.backend.salarycalculator.domain.calc.B2BCalculator;
import com.milosz.podsiadly.backend.salarycalculator.domain.calc.MandateCalculator;
import com.milosz.podsiadly.backend.salarycalculator.domain.calc.SpecificTaskCalculator;
import com.milosz.podsiadly.backend.salarycalculator.domain.calc.UopCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SalaryCalcConfig {

    @Bean
    public UopCalculator uop(SalaryRulesProvider rules) {
        return new UopCalculator(rules);
    }

    @Bean
    public MandateCalculator uz(SalaryRulesProvider rules) {
        return new MandateCalculator(rules);
    }

    @Bean
    public SpecificTaskCalculator uod(SalaryRulesProvider rules) {
        return new SpecificTaskCalculator(rules);
    }

    @Bean
    public B2BCalculator b2b(SalaryRulesProvider rules) {
        return new B2BCalculator(rules);
    }
}
