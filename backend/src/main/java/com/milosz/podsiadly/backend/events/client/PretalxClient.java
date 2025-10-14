package com.milosz.podsiadly.backend.events.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.milosz.podsiadly.backend.events.domain.EventType;
import com.milosz.podsiadly.backend.events.dto.NormalizedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class PretalxClient {

    private final RestTemplate rt;
    private final ObjectMapper om = new ObjectMapper();

    public List<NormalizedEvent> fetchAll() {
        String url = "https://pretalx.com/api/events/?page_size=200";
        List<NormalizedEvent> out = new ArrayList<>();

        while (url != null) {
            String json;
            try {
                json = rt.exchange(url, HttpMethod.GET, new HttpEntity<>(defaultHeaders()), String.class).getBody();
            } catch (Exception ex) {
                log.warn("[pretalx] request failed: {}", url, ex);
                break;
            }
            if (json == null || json.isBlank()) break;

            char first = firstNonWs(json);
            try {
                if (first == '[') {
                    Ev[] arr = om.readValue(json, Ev[].class);
                    for (Ev ev : arr) out.add(toNormalized(ev));
                    url = null;
                } else if (first == '{') {
                    PageDto page = om.readValue(json, PageDto.class);
                    if (page.results != null) {
                        for (Ev ev : page.results) out.add(toNormalized(ev));
                    }
                    url = page.next;
                } else {
                    log.warn("[pretalx] unexpected JSON root char '{}' at {}", first, url);
                    break;
                }
            } catch (Exception ex) {
                log.warn("[pretalx] parse failed at {}: {}", url, ex.toString());
                break;
            }
        }
        log.info("[pretalx] collected {} events", out.size());
        return out;
    }

    private NormalizedEvent toNormalized(Ev ev) {
        String title = ev.name != null
                ? ev.name.getOrDefault("en", ev.name.values().stream().findFirst().orElse(ev.slug))
                : ev.slug;

        Instant from = parseDate(ev.date_from, ev.timezone);
        Instant to   = parseDate(ev.date_to,   ev.timezone);

        String url = "https://pretalx.com/" + ev.slug;

        return new NormalizedEvent(
                "PRETALX",
                ev.slug,
                url,
                title,
                null,
                ev.country,
                ev.state,
                ev.city,
                ev.timezone,
                Boolean.FALSE,
                EventType.CONFERENCE,
                from,
                to,
                "open",
                null, null, null,
                List.of("pretalx"),
                toJsonSafe(ev)
        );
    }

    private static Instant parseDate(String d, String tz){
        try {
            ZoneId z = tz != null ? ZoneId.of(tz) : ZoneOffset.UTC;
            return LocalDate.parse(d).atStartOfDay(z).toInstant();
        } catch (Exception e){ return null; }
    }

    private HttpHeaders defaultHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        h.set(HttpHeaders.USER_AGENT, "EventsIngest/1.0 (+your-app)");
        return h;
    }

    private static char firstNonWs(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c)) return c;
        }
        return 0;
    }

    private String toJsonSafe(Object o) {
        try { return om.writeValueAsString(o); }
        catch (Exception e) { return null; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class PageDto {
        public String next;
        public List<Ev> results;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Ev {
        public String slug;
        public String date_from;
        public String date_to;
        public String timezone;
        public Map<String,String> name;
        public String city;
        public String state;
        public String country;
    }
}
