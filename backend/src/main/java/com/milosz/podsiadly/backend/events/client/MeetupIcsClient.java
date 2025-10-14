package com.milosz.podsiadly.backend.events.client;

import com.milosz.podsiadly.backend.events.domain.EventType;
import com.milosz.podsiadly.backend.events.dto.NormalizedEvent;
import biweekly.Biweekly;
import biweekly.component.VEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

@Component
@EnableConfigurationProperties(MeetupProps.class)
@RequiredArgsConstructor
public class MeetupIcsClient {
    private final RestTemplate rt;
    private final MeetupProps props;

    public List<NormalizedEvent> fetch() {
        List<NormalizedEvent> out = new ArrayList<>();
        for (String g : props.groups()) {
            String url = "https://www.meetup.com/" + g + "/events/ical/";
            String ics = rt.getForObject(url, String.class);
            if (ics == null || ics.isBlank()) continue;

            Biweekly.parse(ics).all()
                    .forEach(cal -> cal.getEvents().forEach(ve -> out.add(map(g, url, ve, ics))));
        }
        return out;
    }

    private static NormalizedEvent map(String group, String fallbackUrl, VEvent ve, String rawIcs) {
        String extId = ve.getUid() != null ? ve.getUid().getValue()
                : (group + "|" + (ve.getSummary() != null ? ve.getSummary().getValue() : "no-title"));

        String url = ve.getUrl() != null ? ve.getUrl().getValue() : fallbackUrl;

        Instant start = ve.getDateStart() != null ? ve.getDateStart().getValue().toInstant() : null;
        Instant end   = ve.getDateEnd()   != null ? ve.getDateEnd().getValue().toInstant()   : null;

        // <-- POPRAWKA: TZID jest parametrem właściwości
        String tz = null;
        if (ve.getDateStart() != null && ve.getDateStart().getParameters() != null) {
            tz = ve.getDateStart().getParameters().getTimezoneId(); // np. "Europe/Warsaw"
        }
        if (tz == null && ve.getDateEnd() != null && ve.getDateEnd().getParameters() != null) {
            tz = ve.getDateEnd().getParameters().getTimezoneId();
        }

        return new NormalizedEvent(
                "MEETUP_ICS",
                extId,
                url,
                ve.getSummary() != null ? ve.getSummary().getValue() : "(no title)",
                ve.getDescription() != null ? ve.getDescription().getValue() : null,
                null, null, null, tz,
                false, EventType.MEETUP,
                start, end, "open",
                ve.getLocation() != null ? ve.getLocation().getValue() : null,
                null, null,
                List.of("meetup", group),
                rawIcs
        );
    }
}
