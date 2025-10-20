package com.milosz.podsiadly.backend.events.client;

import com.milosz.podsiadly.backend.events.dto.NormalizedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@EnableConfigurationProperties(MeetupProps.class)
@RequiredArgsConstructor
public class MeetupIcsClient {

    private final RestTemplate rt;
    private final MeetupProps props;

    public List<NormalizedEvent> fetch() {
        if (props == null || props.groups() == null || props.groups().isEmpty()) {
            return List.of();
        }
        List<NormalizedEvent> out = new ArrayList<>();
        for (String g : props.groups()) {
            String url = "https://www.meetup.com/" + g + "/events/ical/";
            try {
                ResponseEntity<String> resp = rt.exchange(url, HttpMethod.GET, null, String.class);
                log.info("[meetup.ics] {} fetched (ignored by design)", url);
            } catch (HttpStatusCodeException e) {
                if (e.getStatusCode().value() == 503 || e.getStatusCode().value() == 429 || e.getStatusCode().is5xxServerError()) {
                    log.info("[meetup.ics] {} skipped HTTP {}", url, e.getRawStatusCode());
                } else {
                    log.info("[meetup.ics] {} skipped HTTP {}", url, e.getRawStatusCode());
                }
            } catch (Exception e) {
                log.info("[meetup.ics] {} skipped {}", url, e.toString());
            }
        }
        return out;
    }
}
