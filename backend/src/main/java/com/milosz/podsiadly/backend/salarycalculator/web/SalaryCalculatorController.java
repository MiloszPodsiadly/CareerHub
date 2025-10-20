package com.milosz.podsiadly.backend.salarycalculator.web;

import com.milosz.podsiadly.backend.salarycalculator.dto.SalaryRequest;
import com.milosz.podsiadly.backend.salarycalculator.dto.SalaryResponse;
import com.milosz.podsiadly.backend.salarycalculator.repository.SalaryReportEntityMapper;
import com.milosz.podsiadly.backend.salarycalculator.service.SalaryCalculatorService;
import com.milosz.podsiadly.backend.salarycalculator.service.SalaryReportService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/api/salary", produces = MediaType.APPLICATION_JSON_VALUE)
public class SalaryCalculatorController {

    private final SalaryCalculatorService calcService;
    private final SalaryReportService reportService;

    public SalaryCalculatorController(SalaryCalculatorService calcService,
                                      SalaryReportService reportService) {
        this.calcService = calcService;
        this.reportService = reportService;
    }

    @PostMapping("/calculate")
    public SalaryResponse calculate(
            @RequestBody SalaryRequest request,
            @RequestParam(name = "persist", defaultValue = "false") boolean persist,
            @RequestParam(name = "ttlSeconds", required = false) Long ttlSeconds
    ) {
        var result = calcService.calculate(request);

        if (persist) {
            var entity = reportService.persist(calcService.toInput(request), result, ttlSeconds);
            return SalaryReportEntityMapper.toResponse(entity);
        }

        return new SalaryResponse(
                result.gross(),
                result.net(),
                result.yearlyNet(),
                java.util.Map.of(
                        "pension",   result.items().pension(),
                        "disability",result.items().disability(),
                        "sickness",  result.items().sickness(),
                        "health",    result.items().health(),
                        "pit",       result.items().pit()
                ),
                result.details(),
                result.proportions(),
                result.monthly().stream().map(m ->
                        new SalaryResponse.MonthlyRow(
                                m.month(), m.gross(), m.social(), m.health(), m.pit(), m.net()
                        )
                ).toList()
        );
    }

    @GetMapping("/report/{id}")
    public SalaryResponse getReport(@PathVariable String id) {
        return reportService.get(id);
    }
}
