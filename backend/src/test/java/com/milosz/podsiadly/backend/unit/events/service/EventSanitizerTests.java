package com.milosz.podsiadly.backend.unit.events.service;

import com.milosz.podsiadly.backend.events.service.EventSanitizer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class EventSanitizerTests {

    @Test
    void should_canonicalize_url_by_normalizing_host_path_and_sorted_query() {
        String result = EventSanitizer.canonicalizeUrl("HTTPS://Example.com/events/?b=2&a=1");

        assertThat(result).isEqualTo("https://example.com/events?a=1&b=2");
    }

    @Test
    void should_fallback_to_trimmed_compact_string_when_url_is_invalid() {
        String result = EventSanitizer.canonicalizeUrl("  https://exa mple.com/a b  ");

        assertThat(result).isEqualTo("https://example.com/ab");
    }

    @Test
    void should_build_canonical_external_id_from_url_when_external_id_is_blank() {
        String result = EventSanitizer.canonicalExternalId("pretalx", " ", "https://example.com/e?z=2&a=1");

        assertThat(result).isEqualTo("https://example.com/e?a=1&z=2");
    }

    @Test
    void should_convert_text_to_title_case_with_normalized_whitespace() {
        String result = EventSanitizer.titleCase("  wARSAW   jS/MEETUP  ");

        assertThat(result).isEqualTo("Warsaw Js/Meetup");
    }
}
