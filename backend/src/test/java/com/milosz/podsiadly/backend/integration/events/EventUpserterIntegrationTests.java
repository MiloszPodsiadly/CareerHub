package com.milosz.podsiadly.backend.integration.events;

import com.milosz.podsiadly.backend.events.domain.EventType;
import com.milosz.podsiadly.backend.events.dto.NormalizedEvent;
import com.milosz.podsiadly.backend.events.repository.TechEventRepository;
import com.milosz.podsiadly.backend.events.service.EventUpserter;
import com.milosz.podsiadly.backend.integration.BackendIntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class EventUpserterIntegrationTests extends BackendIntegrationTestBase {

    @Autowired
    private EventUpserter eventUpserter;

    @Autowired
    private TechEventRepository techEventRepository;

    @AfterEach
    void cleanUp() {
        techEventRepository.deleteAll();
    }

    @Test
    @Transactional
    void should_upsert_same_event_and_replace_tags_without_creating_duplicate_row() {
        NormalizedEvent first = new NormalizedEvent(
                "pretalx",
                "event-1",
                "https://example.com/events/1?b=2&a=1",
                "JVM Summit",
                "first payload",
                "PL",
                "mazowieckie",
                "warsaw",
                "Europe/Warsaw",
                true,
                EventType.CONFERENCE,
                Instant.parse("2026-05-10T10:00:00Z"),
                Instant.parse("2026-05-10T18:00:00Z"),
                "PUBLISHED",
                "Expo",
                52.23,
                21.01,
                List.of("java", "spring"),
                "{\"version\":1}"
        );
        NormalizedEvent updated = new NormalizedEvent(
                "pretalx",
                "event-1",
                "https://example.com/events/1?a=1&b=2",
                "JVM Summit Updated",
                "second payload",
                "PL",
                "mazowieckie",
                "warsaw",
                "Europe/Warsaw",
                true,
                EventType.CONFERENCE,
                Instant.parse("2026-05-10T10:00:00Z"),
                Instant.parse("2026-05-10T18:00:00Z"),
                "PUBLISHED",
                "Expo",
                52.23,
                21.01,
                List.of("kotlin"),
                "{\"version\":2}"
        );

        Long firstId = eventUpserter.upsert(first);
        Long updatedId = eventUpserter.upsert(updated);

        assertThat(updatedId).isEqualTo(firstId);
        assertThat(techEventRepository.count()).isEqualTo(1);

        var stored = techEventRepository.findById(firstId).orElseThrow();
        assertThat(stored.getTitle()).isEqualTo("JVM Summit Updated");
        assertThat(stored.getUrl()).isEqualTo("https://example.com/events/1?a=1&b=2");
        assertThat(stored.getTags()).containsExactly("kotlin");
        assertThat(stored.getFirstSeenAt()).isNotNull();
        assertThat(stored.getLastSeenAt()).isNotNull();
    }
}
