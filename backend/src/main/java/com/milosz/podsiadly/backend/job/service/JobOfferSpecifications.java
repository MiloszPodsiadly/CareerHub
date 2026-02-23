package com.milosz.podsiadly.backend.job.service;

import com.milosz.podsiadly.backend.job.domain.ContractType;
import com.milosz.podsiadly.backend.job.domain.JobLevel;
import com.milosz.podsiadly.backend.job.domain.JobOffer;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
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
        return text(q, true);
    }

    public static Specification<JobOffer> text(String q, boolean includeDescription) {
        if (q == null || q.isBlank()) return null;
        String like = "%" + q.toLowerCase() + "%";
        return (r, qry, cb) -> {
            if (includeDescription) {
                return cb.or(
                        cb.like(cb.lower(r.get("title")), like),
                        cb.like(cb.lower(r.get("description")), like)
                );
            }
            return cb.like(cb.lower(r.get("title")), like);
        };
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
            var sq = query.subquery(Integer.class);
            var sqRoot = sq.correlate(root);
            Join<JobOffer, String> tagJoin = sqRoot.join("techTags", JoinType.INNER);

            sq.select(cb.literal(1))
                    .where(tagJoin.in(tags));

            return cb.exists(sq);
        };
    }

    public static Specification<JobOffer> salaryBetween(Integer min, Integer max) {
        return (r, q, cb) -> cb.and(
                min != null
                        ? cb.greaterThanOrEqualTo(upperSalary(r, cb), min)
                        : cb.conjunction(),
                max != null
                        ? cb.lessThanOrEqualTo(lowerSalary(r, cb), max)
                        : cb.conjunction()
        );
    }

    public static Specification<JobOffer> postedAfter(Instant after) {
        if (after == null) return null;
        return (r, q, cb) -> cb.greaterThanOrEqualTo(r.get("publishedAt"), after);
    }

    public static Specification<JobOffer> contractAny(Collection<ContractType> wanted) {
        if (wanted == null || wanted.isEmpty()) return null;

        return (root, query, cb) -> {
            var inSingle = root.get("contract").in(wanted);
            var sq = query.subquery(Integer.class);
            var sqRoot = sq.correlate(root);
            Join<JobOffer, ContractType> j = sqRoot.join("contracts", JoinType.INNER);

            sq.select(cb.literal(1))
                    .where(j.in(wanted));

            var inMany = cb.exists(sq);

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
                                    cb.isNull(root.get("salaryMax")),
                                    cb.isNull(root.get("salaryNormMonthMin")),
                                    cb.isNull(root.get("salaryNormMonthMax"))
                            ),
                            1
                    )
                    .otherwise(0);

            var bestSalary = upperSalary(root, cb);
            var floorSalary = lowerSalary(root, cb);

            query.orderBy(
                    cb.asc(noSalaryLast),
                    cb.desc(bestSalary),
                    cb.desc(floorSalary),
                    cb.desc(root.get("publishedAt")),
                    cb.desc(root.get("id"))
            );

            return cb.conjunction();
        };
    }

    private static Expression<Integer> upperSalary(Root<JobOffer> root, jakarta.persistence.criteria.CriteriaBuilder cb) {
        var c = cb.<Integer>coalesce();
        c.value(root.get("salaryMax"));
        c.value(root.get("salaryMin"));
        c.value(root.get("salaryNormMonthMax"));
        c.value(root.get("salaryNormMonthMin"));
        return c;
    }

    private static Expression<Integer> lowerSalary(Root<JobOffer> root, jakarta.persistence.criteria.CriteriaBuilder cb) {
        var c = cb.<Integer>coalesce();
        c.value(root.get("salaryMin"));
        c.value(root.get("salaryMax"));
        c.value(root.get("salaryNormMonthMin"));
        c.value(root.get("salaryNormMonthMax"));
        return c;
    }
}
