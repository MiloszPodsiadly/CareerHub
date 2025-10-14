package com.milosz.podsiadly.backend.events.web;

import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SortParamParser {

    private SortParamParser() {}

    public static final Set<String> DEFAULT_ALLOWED =
            Set.of("startAt", "endAt", "title", "country", "city", "firstSeenAt", "lastSeenAt");

    public static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("startAt"));

    public static Sort parse(String raw) {
        return parse(raw, DEFAULT_ALLOWED, DEFAULT_SORT);
    }

    public static Sort parse(String raw, Set<String> allowed, Sort defaultSort) {
        if (raw == null || raw.isBlank()) return defaultSort;

        List<Sort.Order> orders = new ArrayList<>();
        for (String part : raw.split("[;|]")) {
            if (part == null || part.isBlank()) continue;

            String[] a = part.split(",");
            String prop = a[0].trim();
            if (!allowed.contains(prop)) continue;

            boolean desc = a.length > 1 && "desc".equalsIgnoreCase(a[1].trim());
            orders.add(new Sort.Order(desc ? Sort.Direction.DESC : Sort.Direction.ASC, prop));
        }
        return orders.isEmpty() ? defaultSort : Sort.by(orders);
    }
}

