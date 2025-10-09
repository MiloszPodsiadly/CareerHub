// src/main/java/com/milosz/podsiadly/backend/job/service/JobOfferSpecifications.java
package com.milosz.podsiadly.backend.job.service;

import com.milosz.podsiadly.backend.job.domain.ContractType;
import com.milosz.podsiadly.backend.job.domain.JobLevel;
import com.milosz.podsiadly.backend.job.domain.JobOffer;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

public final class JobOfferSpecifications {
    private JobOfferSpecifications() {}

    public static Specification<JobOffer> active() {
        return (r,q,cb) -> cb.isTrue(r.get("active"));
    }

    public static Specification<JobOffer> text(String q) {
        if (q == null || q.isBlank()) return null;
        String like = "%" + q.toLowerCase() + "%";
        return (r,qry,cb) -> cb.or(
                cb.like(cb.lower(r.get("title")), like),
                cb.like(cb.lower(r.get("description")), like)
        );
    }

    public static Specification<JobOffer> byCity(String city) {
        if (city == null || city.isBlank()) return null;
        return (r,q,cb) -> cb.equal(cb.lower(r.join("city", JoinType.LEFT).get("name")), city.toLowerCase());
    }

    public static Specification<JobOffer> remote(Boolean remote) {
        if (remote == null) return null;
        return (r,q,cb) -> cb.equal(r.get("remote"), remote);
    }

    public static Specification<JobOffer> level(JobLevel lvl) {
        if (lvl == null) return null;
        return (r,q,cb) -> cb.equal(r.get("level"), lvl);
    }

    public static Specification<JobOffer> techAny(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        return (root, query, cb) -> {
            query.distinct(true);
            var tagJoin = root.join("techTags", JoinType.LEFT);
            return tagJoin.in(tags);
        };
    }

    public static Specification<JobOffer> salaryBetween(Integer min, Integer max) {
        return (r,q,cb) -> cb.and(
                min != null ? cb.greaterThanOrEqualTo(r.get("salaryMax"), min) : cb.conjunction(),
                max != null ? cb.lessThanOrEqualTo(r.get("salaryMin"), max) : cb.conjunction()
        );
    }

    public static Specification<JobOffer> postedAfter(Instant after) {
        if (after == null) return null;
        return (r,q,cb) -> cb.greaterThanOrEqualTo(r.get("publishedAt"), after);
    }

    public static Specification<JobOffer> contract(ContractType c) {
        if (c == null) return null;
        return (r,q,cb) -> cb.equal(r.get("contract"), c);
    }

    /** withSalary=true → wymagaj przynajmniej jednej wartości widełek */
    public static Specification<JobOffer> withSalary(Boolean with) {
        if (with == null || !with) return null;
        return (r,q,cb) -> cb.or(cb.isNotNull(r.get("salaryMin")), cb.isNotNull(r.get("salaryMax")));
    }
}
