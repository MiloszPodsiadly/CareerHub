package com.milosz.podsiadly.backend.job.service;

import com.milosz.podsiadly.backend.job.domain.ContractType;
import com.milosz.podsiadly.backend.job.domain.JobLevel;
import com.milosz.podsiadly.backend.job.domain.JobOffer;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class JobOfferSpecifications {
    private JobOfferSpecifications() {}

    public static Specification<JobOffer> active() {
        return (r, q, cb) -> cb.isTrue(r.get("active"));
    }

    public static Specification<JobOffer> text(String q) {
        if (q == null || q.isBlank()) return null;
        String like = "%" + q.toLowerCase() + "%";
        return (r, qry, cb) -> cb.or(
                cb.like(cb.lower(r.get("title")), like),
                cb.like(cb.lower(r.get("description")), like)
        );
    }

    public static Specification<JobOffer> byCity(String city) {
        if (city == null || city.isBlank()) return null;
        String lc = city.toLowerCase();
        return (r, q, cb) -> cb.equal(cb.lower(r.join("city", JoinType.LEFT).get("name")), lc);
    }

    public static Specification<JobOffer> remote(Boolean remote) {
        if (remote == null) return null;
        return (r, q, cb) -> cb.equal(r.get("remote"), remote);
    }

    public static Specification<JobOffer> level(JobLevel lvl) {
        if (lvl == null) return null;
        return (r, q, cb) -> cb.equal(r.get("level"), lvl);
    }

    public static Specification<JobOffer> techAny(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        return (root, query, cb) -> {
            query.distinct(true);
            Join<JobOffer, String> tagJoin = root.join("techTags", JoinType.LEFT);
            return tagJoin.in(tags);
        };
    }

    public static Specification<JobOffer> salaryBetween(Integer min, Integer max) {
        return (r, q, cb) -> cb.and(
                min != null ? cb.greaterThanOrEqualTo(r.get("salaryMax"), min) : cb.conjunction(),
                max != null ? cb.lessThanOrEqualTo(r.get("salaryMin"), max) : cb.conjunction()
        );
    }

    public static Specification<JobOffer> postedAfter(Instant after) {
        if (after == null) return null;
        return (r, q, cb) -> cb.greaterThanOrEqualTo(r.get("publishedAt"), after);
    }

    public static Specification<JobOffer> contractAny(Collection<ContractType> wanted) {
        if (wanted == null || wanted.isEmpty()) return null;
        return (root, query, cb) -> {
            query.distinct(true);
            var inSingle = root.get("contract").in(wanted);
            Join<JobOffer, ContractType> j = root.join("contracts", JoinType.LEFT);
            var inMany = j.in(wanted);
            return cb.or(inSingle, inMany);
        };
    }

    public static Specification<JobOffer> contract(ContractType c) {
        return (c == null) ? null : contractAny(Set.of(c));
    }

    public static Specification<JobOffer> withSalary(Boolean with) {
        if (with == null || !with) return null;
        return (r, q, cb) -> cb.or(cb.isNotNull(r.get("salaryMin")), cb.isNotNull(r.get("salaryMax")));
    }
    public static Specification<JobOffer> orderByHighestSalaryNullsLast() {
        return (root, query, cb) -> {
            if (query.getResultType() == Long.class || query.getResultType() == long.class) {
                return cb.conjunction();
            }

            var noSalaryLast = cb.<Integer>selectCase()
                    .when(
                            cb.and(
                                    cb.isNull(root.get("salaryMin")),
                                    cb.isNull(root.get("salaryMax"))
                            ),
                            1
                    )
                    .otherwise(0);

            query.orderBy(
                    cb.asc(noSalaryLast),
                    cb.desc(cb.coalesce(root.get("salaryMax"), root.get("salaryMin"))),
                    cb.desc(root.get("salaryMin")),
                    cb.desc(root.get("publishedAt")),
                    cb.desc(root.get("id"))
            );

            return cb.conjunction();
        };
    }
}
