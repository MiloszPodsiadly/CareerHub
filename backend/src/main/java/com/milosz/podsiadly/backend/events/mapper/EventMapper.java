package com.milosz.podsiadly.backend.events.mapper;

import com.milosz.podsiadly.backend.events.domain.TechEvent;
import com.milosz.podsiadly.backend.events.dto.EventDetailDto;
import com.milosz.podsiadly.backend.events.dto.EventListDto;

import java.util.ArrayList;
import java.util.List;

public final class EventMapper {
    private EventMapper(){}

    private static List<String> copyTags(List<String> src){
        return (src == null) ? List.of() : List.copyOf(new ArrayList<>(src));
    }

    public static EventListDto toList(TechEvent e) {
        return new EventListDto(
                e.getId(),
                e.getSource(),
                e.getExternalId(),
                e.getUrl(),
                e.getTitle(),
                e.getCountry(),
                e.getCity(),
                e.getOnline(),
                e.getType(),
                e.getStartAt(),
                e.getEndAt(),
                copyTags(e.getTags())
        );
    }

    public static EventDetailDto toDetail(TechEvent e) {
        return new EventDetailDto(
                e.getId(),
                e.getSource(),
                e.getExternalId(),
                e.getUrl(),
                e.getTitle(),
                e.getDescription(),
                e.getCountry(),
                e.getCity(),
                e.getOnline(),
                e.getType(),
                e.getStartAt(),
                e.getEndAt(),
                e.getVenue(),
                e.getLatitude(),
                e.getLongitude(),
                copyTags(e.getTags())
        );
    }
}
