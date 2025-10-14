package com.milosz.podsiadly.backend.events.service;

import com.milosz.podsiadly.backend.events.domain.EventType;
import com.milosz.podsiadly.backend.events.domain.TechEvent;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

public final class EventSpecifications {
    private EventSpecifications(){}

    public static Specification<TechEvent> text(String q){
        if (q == null || q.isBlank()) return null;
        String like = "%"+q.toLowerCase()+"%";
        return (r, cq, cb) -> cb.or(
                cb.like(cb.lower(r.get("title")), like),
                cb.like(cb.lower(r.get("description")), like)
        );
    }

    public static Specification<TechEvent> country(String name){
        if (name == null || name.isBlank()) return null;
        return (r,cq,cb) -> cb.equal(cb.lower(r.get("country")), name.toLowerCase());
    }

    public static Specification<TechEvent> city(String city){
        if (city == null || city.isBlank()) return null;
        return (r,cq,cb) -> cb.equal(cb.lower(r.get("city")), city.toLowerCase());
    }

    public static Specification<TechEvent> type(EventType t){
        if (t == null) return null;
        return (r,cq,cb) -> cb.equal(r.get("type"), t);
    }

    public static Specification<TechEvent> online(Boolean online){
        if (online == null) return null;
        return (r,cq,cb) -> cb.equal(r.get("online"), online);
    }

    public static Specification<TechEvent> between(Instant from, Instant to){
        return (r,cq,cb) -> cb.and(
                from != null ? cb.greaterThanOrEqualTo(r.get("startAt"), from) : cb.conjunction(),
                to   != null ? cb.lessThanOrEqualTo(r.get("startAt"), to)   : cb.conjunction()
        );
    }

    public static Specification<TechEvent> tagsAny(List<String> tags){
        if (tags == null || tags.isEmpty()) return null;
        return (root, query, cb) -> {
            var join = root.join("tags");
            query.distinct(true);
            return join.in(tags);
        };
    }
}
