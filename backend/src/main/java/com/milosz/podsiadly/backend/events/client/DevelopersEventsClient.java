package com.milosz.podsiadly.backend.events.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.milosz.podsiadly.backend.events.domain.EventType;
import com.milosz.podsiadly.backend.events.dto.NormalizedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

@Component @RequiredArgsConstructor
public class DevelopersEventsClient {
    private final RestTemplate rt;

    public List<NormalizedEvent> fetch() {
        var arr = rt.getForObject("https://developers.events/all-events.json", DevEvent[].class);
        if (arr == null) return List.of();

        List<NormalizedEvent> out = new ArrayList<>();
        for (var it : arr) {
            Instant start = it.date != null && it.date.length > 0 ? Instant.ofEpochMilli(it.date[0]) : null;
            Instant end   = it.date != null && it.date.length > 1 ? Instant.ofEpochMilli(it.date[1]) : start;
            out.add(new NormalizedEvent(
                    "DEVELOPERS_EVENTS",
                    it.hyperlink != null ? it.hyperlink : it.name,
                    it.hyperlink,
                    it.name,
                    null,
                    normCC(it.country), null, it.city, null,
                    it.online, EventType.CONFERENCE,
                    start, end, it.status,
                    null, null, null,
                    mapTags(it.tags),
                    toJson(it)
            ));
        }
        return out;
    }

    private static String normCC(String c){ return c==null?null:c.trim().length()==2?c.toUpperCase():c.toUpperCase(); }
    private static List<String> mapTags(List<Map<String,String>> tags){
        if (tags==null) return List.of();
        return tags.stream().map(m->m.getOrDefault("value","")).filter(s->!s.isBlank()).toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class DevEvent {
        public String name, hyperlink, city, country, status;
        public Boolean online;
        public long[] date;
        public List<Map<String,String>> tags;
    }

    private static String toJson(Object o){
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o); }
        catch (Exception e){ return null; }
    }
}
