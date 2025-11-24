package com.milosz.podsiadly.backend.events.web;

import com.milosz.podsiadly.backend.events.domain.EventType;
import com.milosz.podsiadly.backend.events.dto.*;
import com.milosz.podsiadly.backend.events.service.EventQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

import static org.springframework.format.annotation.DateTimeFormat.ISO;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventQueryService svc;

    @GetMapping
    public Page<EventListDto> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) EventType type,
            @RequestParam(required = false) Boolean online,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, name = "tag") List<String> tags,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(value = "sort", required = false) String sort
    ) {
        Sort s = SortParamParser.parse(sort);
        Pageable p = PageRequest.of(Math.max(0, page), Math.min(200, size), s);
        return svc.search(q, country, city, type, online, from, to, tags, p);
    }

    @GetMapping("/{id}")
    public EventDetailDto get(@PathVariable Long id) { return svc.get(id); }

    @GetMapping("/geo/countries")
    public List<CountryDto> countries() { return svc.countries(); }

    @GetMapping("/geo/cities")
    public List<CityDto> cities(@RequestParam String country) { return svc.cities(country); }
}
