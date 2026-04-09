package com.milosz.podsiadly.backend.unit.events.web;

import com.milosz.podsiadly.backend.events.web.SortParamParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class SortParamParserTests {

    @Test
    void should_return_default_sort_when_raw_is_blank() {
        Sort result = SortParamParser.parse("  ");

        assertThat(result).isEqualTo(SortParamParser.DEFAULT_SORT);
    }

    @Test
    void should_parse_multiple_allowed_orders_when_raw_contains_supported_fields() {
        Sort result = SortParamParser.parse(
                "startAt,desc|title,asc",
                Set.of("startAt", "title"),
                Sort.by("id")
        );

        assertThat(result).containsExactly(
                Sort.Order.desc("startAt"),
                Sort.Order.asc("title")
        );
    }

    @Test
    void should_ignore_disallowed_fields_and_fallback_when_nothing_valid_remains() {
        Sort result = SortParamParser.parse(
                "salary,desc|unknown,asc",
                Set.of("startAt", "title"),
                Sort.by(Sort.Order.desc("startAt"))
        );

        assertThat(result).containsExactly(Sort.Order.desc("startAt"));
    }
}
