package com.milosz.podsiadly.backend.salarycalculator.repository;

import com.milosz.podsiadly.backend.salarycalculator.domain.model.CalculationInput;
import com.milosz.podsiadly.backend.salarycalculator.domain.model.CalculationResult;
import com.milosz.podsiadly.backend.salarycalculator.dto.SalaryResponse;
import com.milosz.podsiadly.backend.salarycalculator.repository.entity.SalaryReportEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SalaryReportEntityMapper {
    private SalaryReportEntityMapper(){}

    public static SalaryReportEntity toEntity(CalculationInput in, CalculationResult r, Long ttlSeconds) {
        var e = new SalaryReportEntity();
        e.setGross(r.gross());
        e.setNet(r.net());
        e.setYearlyNet(r.yearlyNet());
        e.setItems(new SalaryReportEntity.Items(
                r.items().pension(), r.items().disability(), r.items().sickness(), r.items().health(), r.items().pit()
        ));
        e.setDetails(r.details());
        e.setProportions(r.proportions());
        e.setMonthly(r.monthly().stream()
                .map(m -> new SalaryReportEntity.Monthly(m.month(), m.gross(), m.social(), m.health(), m.pit(), m.net()))
                .toList());

        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("pit0", in.pit0());

        e.setRequest(new SalaryReportEntity.RequestSnapshot(
                in.contractType().name(), in.amountMode().name(), in.year(), opts
        ));
        e.setTtl(ttlSeconds);
        return e;
    }

    public static SalaryResponse toResponse(SalaryReportEntity e) {
        Map<String, java.math.BigDecimal> items = new LinkedHashMap<>();
        items.put("pension", e.getItems().getPension());
        items.put("disability", e.getItems().getDisability());
        items.put("sickness", e.getItems().getSickness());
        items.put("health", e.getItems().getHealth());
        items.put("pit", e.getItems().getPit());

        List<SalaryResponse.MonthlyRow> monthly = e.getMonthly().stream()
                .map(m -> new SalaryResponse.MonthlyRow(m.getMonth(), m.getGross(), m.getSocial(), m.getHealth(), m.getPit(), m.getNet()))
                .toList();

        return new SalaryResponse(
                e.getGross(),
                e.getNet(),
                e.getYearlyNet(),
                items,
                e.getDetails(),
                e.getProportions(),
                monthly
        );
    }
}
