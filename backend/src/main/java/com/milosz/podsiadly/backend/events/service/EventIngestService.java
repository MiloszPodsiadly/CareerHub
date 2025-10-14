package com.milosz.podsiadly.backend.events.service;

import com.milosz.podsiadly.backend.events.client.ConfsTechRepoClient;
import com.milosz.podsiadly.backend.events.client.DevelopersEventsClient;
import com.milosz.podsiadly.backend.events.client.MeetupIcsClient;
import com.milosz.podsiadly.backend.events.client.PretalxClient;
import com.milosz.podsiadly.backend.events.dto.NormalizedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Supplier;

import static com.milosz.podsiadly.backend.events.service.EventSanitizer.canonicalExternalId;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventIngestService {

    private final PretalxClient pretalx;
    private final ConfsTechRepoClient confsTechRepo;
    private final DevelopersEventsClient developersEvents;
    private final MeetupIcsClient meetupIcs;
    private final EventUpserter upserter;

    public int runAll() {
        var all = new ArrayList<NormalizedEvent>();
        all.addAll(safeFetch("pretalx", pretalx::fetchAll));
        all.addAll(safeFetch("confs.tech", confsTechRepo::fetch));
        all.addAll(safeFetch("developers.events", developersEvents::fetch));
        all.addAll(safeFetch("meetup.ics", meetupIcs::fetch));

        return upsertAll(all);
    }

    public int upsertAll(List<NormalizedEvent> events) {
        Map<String, NormalizedEvent> uniq = new LinkedHashMap<>();
        for (var n : events) {
            if (n == null) continue;
            String ext = canonicalExternalId(n.source(), n.externalId(), n.url());
            if (ext == null) continue;
            uniq.putIfAbsent(n.source() + "|" + ext, n);
        }

        int cnt = 0;
        for (var n : uniq.values()) {
            try {
                upserter.upsert(n);
                cnt++;
            } catch (Exception ex) {
                log.warn("[ingest] upsert failed for {} {}: {}", n.source(), n.url(), ex.toString());
            }
        }
        log.info("[ingest] upserted {} events ({} unique, {} raw)", cnt, uniq.size(), events.size());
        return cnt;
    }

    private List<NormalizedEvent> safeFetch(String label, Supplier<List<NormalizedEvent>> supplier) {
        try {
            var list = supplier.get();
            int s = list != null ? list.size() : 0;
            log.info("[{}] fetched {} events", label, s);
            return list != null ? list : List.of();
        } catch (Exception ex) {
            log.warn("[{}] fetch failed: {}", label, ex.toString());
            return List.of();
        }
    }
}
