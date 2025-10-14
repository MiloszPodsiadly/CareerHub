package com.milosz.podsiadly.backend.events.service;

import com.milosz.podsiadly.backend.events.dto.NormalizedEvent;
import com.milosz.podsiadly.backend.events.repository.TechEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static com.milosz.podsiadly.backend.events.service.EventSanitizer.*;

@Service
@RequiredArgsConstructor
public class EventUpserter {

    private final TechEventRepository repo;

    public Long upsert(NormalizedEvent n) {
        final String src = n.source();
        String cid = canonicalExternalId(src, n.externalId(), n.url());
        if (cid == null) cid = "fp:" + fingerprint(n);
        final String extId = cid;

        Instant now = Instant.now();
        repo.upsert(
                src,
                extId,
                trimOrNull(canonicalizeUrl(n.url())),
                trimOrNull(n.title()),
                trimOrNull(n.description()),
                trimOrNull(n.country()),
                titleCase(n.region()),
                titleCase(n.city()),
                trimOrNull(n.timezone()),
                Boolean.TRUE.equals(n.online()),
                n.type() != null ? n.type().name() : null,
                n.startAt(),
                n.endAt(),
                trimOrNull(n.status()),
                titleCase(n.venue()),
                n.lat(),
                n.lon(),
                now,
                n.rawJson(),
                fingerprint(n)
        );

        Long id = repo.findId(src, extId);
        if (id != null) {
            replaceTags(id, n.tags());
        }
        return id;
    }

    private void replaceTags(Long id, List<String> tags) {
        repo.deleteTags(id);
        if (tags == null) return;
        tags.stream()
                .map(EventSanitizer::trimOrNull)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .limit(100)
                .forEach(t -> repo.insertTag(id, t.length() <= 128 ? t : t.substring(0,128)));
    }

    private String fingerprint(NormalizedEvent n) {
        String base = (n.title()+"|"+n.startAt()+"|"+n.city()+"|"+n.country())
                .toLowerCase(Locale.ROOT);
        try {
            var dig = MessageDigest.getInstance("SHA-256").digest(base.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(dig);
        } catch (Exception e) { return null; }
    }
}
