package com.milosz.podsiadly.backend.events.service;

import com.milosz.podsiadly.backend.events.domain.TechEvent;
import com.milosz.podsiadly.backend.events.domain.EventType;
import com.milosz.podsiadly.backend.events.dto.CityDto;
import com.milosz.podsiadly.backend.events.dto.CountryDto;
import com.milosz.podsiadly.backend.events.dto.EventDetailDto;
import com.milosz.podsiadly.backend.events.dto.EventListDto;
import com.milosz.podsiadly.backend.events.mapper.EventMapper;
import com.milosz.podsiadly.backend.events.repository.TechEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EventQueryService {

    private final TechEventRepository repo;

    @Transactional(readOnly = true)
    public Page<EventListDto> search(
            String q, String country, String city, EventType type,
            Boolean online, Instant from, Instant to, List<String> tags,
            Pageable pageable
    ) {
        Specification<TechEvent> spec = Specification.allOf(
                EventSpecifications.text(q),
                EventSpecifications.country(country),
                EventSpecifications.city(city),
                EventSpecifications.type(type),
                EventSpecifications.online(online),
                EventSpecifications.between(from, to),
                EventSpecifications.tagsAny(tags)
        );

        return repo.findAll(spec, pageable).map(EventMapper::toList);
    }

    @Transactional(readOnly = true)
    public EventDetailDto get(Long id) {
        var e = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Event not found: " + id));
        return EventMapper.toDetail(e);
    }

    @Transactional(readOnly = true)
    public List<CountryDto> countries() {
        var rows = repo.countryCounts();
        List<CountryDto> out = new ArrayList<>();
        for (Object[] r : rows) {
            String name = (String) r[0];
            long cnt = ((Number) r[1]).longValue();
            out.add(new CountryDto(name, cnt));
        }
        out.sort(Comparator.comparing(CountryDto::name, Comparator.nullsLast(String::compareTo)));
        return out;
    }

    @Transactional(readOnly = true)
    public List<CityDto> cities(String country) {
        var rows = repo.cityCounts(country);
        List<CityDto> out = new ArrayList<>();
        for (Object[] r : rows) {
            out.add(new CityDto((String) r[0], ((Number) r[1]).longValue()));
        }
        out.sort(Comparator.comparing(CityDto::name, Comparator.nullsLast(String::compareTo)));
        return out;
    }

    private String countryName(String code) {
        if (code == null || code.isBlank()) return null;
        return new Locale("", code).getDisplayCountry(new Locale("pl", "PL"));
    }
}
