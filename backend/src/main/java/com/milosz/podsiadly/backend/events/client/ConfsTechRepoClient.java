package com.milosz.podsiadly.backend.events.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.milosz.podsiadly.backend.events.domain.EventType;
import com.milosz.podsiadly.backend.events.dto.NormalizedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.text.Normalizer;
import java.time.*;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConfsTechRepoClient {

    private final RestTemplate rt;
    private final ObjectMapper om = new ObjectMapper();

    private static final List<String> FILES = List.of(
            "https://raw.githubusercontent.com/tech-conferences/conference-data/master/conferences/2025/general.json",
            "https://raw.githubusercontent.com/tech-conferences/conference-data/master/conferences/2025/javascript.json"
    );

    public List<NormalizedEvent> fetch() {
        List<NormalizedEvent> out = new ArrayList<>();

        for (String url : FILES) {
            String json;
            try {
                json = rt.exchange(url, HttpMethod.GET, new HttpEntity<>(headers()), String.class).getBody();
            } catch (Exception ex) {
                log.warn("[confs.tech] request failed: {}", url, ex);
                continue;
            }
            if (json == null || json.isBlank()) continue;

            try {
                CEvent[] arr = om.readValue(json, CEvent[].class);
                for (CEvent e : arr) {
                    out.add(map(e));
                }
                log.info("[confs.tech] {} -> {} events", url, arr.length);
            } catch (Exception ex) {
                log.warn("[confs.tech] parse failed at {}: {}", url, ex.toString());
            }
        }
        log.info("[confs.tech] collected {} events total", out.size());
        return out;
    }


    private NormalizedEvent map(CEvent e) {
        var cityAndRegion = splitCityRegion(e.city);

        String country = normalizeCountryText(e.country != null ? e.country : e.countryCode);

        Instant start = parseLocalDate(e.startDate, e.timezone);
        Instant end   = parseLocalDate(e.endDate,   e.timezone);

        List<String> tags = new ArrayList<>();
        if (e.locales != null && !e.locales.isBlank()) {
            for (String t : e.locales.split("[,\\s]+")) {
                if (!t.isBlank()) tags.add(t.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (Boolean.TRUE.equals(e.online)) tags.add("online");

        return new NormalizedEvent(
                "CONFS_TECH",
                nz(e.url, e.name),
                e.url,
                e.name,
                e.description,
                country,
                cityAndRegion[1],
                cityAndRegion[0],
                e.timezone,
                e.online != null ? e.online : Boolean.FALSE,
                EventType.CONFERENCE,
                start,
                end != null ? end : start,
                "open",
                e.venue,
                e.latitude,
                e.longitude,
                tags,
                toJson(e)
        );
    }

    private static Instant parseLocalDate(String isoLocalDate, String tz) {
        try {
            ZoneId z = tz != null ? ZoneId.of(tz) : ZoneOffset.UTC;
            return LocalDate.parse(isoLocalDate).atStartOfDay(z).toInstant();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String[] splitCityRegion(String cityRaw) {
        if (cityRaw == null || cityRaw.isBlank()) return new String[]{null, null};
        String s = cityRaw.trim();
        int comma = s.indexOf(',');
        if (comma > 0 && comma < s.length() - 1) {
            String city = s.substring(0, comma).trim();
            String region = s.substring(comma + 1).trim();
            return new String[]{titleCase(city), region};
        }
        return new String[]{titleCase(s), null};
    }

    private static String normalizeCountryText(String in) {
        if (in == null || in.isBlank()) return null;
        String s = in.trim();

        String noDots = s.replaceAll("(?i)\\bU\\.?S\\.?A\\.?\\b", "USA")
                .replaceAll("(?i)\\bU\\.?K\\.?\\b", "UK");

        String n = Normalizer.normalize(noDots, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('’','\'')
                .replaceAll("\\s{2,}", " ")
                .trim();

        return titleCase(n);
    }

    private static String titleCase(String s) {
        if (s == null) return null;
        StringBuilder out = new StringBuilder(s.length());
        boolean start = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (start && Character.isLetter(c)) {
                out.append(Character.toUpperCase(c));
                start = false;
            } else {
                out.append(Character.toLowerCase(c));
            }
            if (c == ' ' || c == '-' || c == '\'' || c == '/') start = true;
        }
        return out.toString();
    }

    private static String nz(String v, String fb) { return v != null ? v : fb; }


    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        h.set(HttpHeaders.USER_AGENT, "EventsIngest/1.0 (+your-app)");
        return h;
    }

    private String toJson(Object o) {
        try { return om.writeValueAsString(o); }
        catch (Exception e) { return null; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class CEvent {
        public String name;
        public String url;
        public String description;
        public String startDate;
        public String endDate;
        public Boolean online;
        public String city;
        public String country;
        public String countryCode;
        public String timezone;
        public String venue;

        public String locales;
        public String cocUrl;
        public String cfpUrl;
        public String cfpEndDate;
        public String twitter;

        public Double latitude;
        public Double longitude;
        public List<String> topics;
    }
}
