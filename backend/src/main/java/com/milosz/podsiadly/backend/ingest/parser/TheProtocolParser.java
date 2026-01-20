package com.milosz.podsiadly.backend.ingest.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.milosz.podsiadly.backend.job.domain.ContractType;
import com.milosz.podsiadly.backend.job.domain.JobLevel;
import com.milosz.podsiadly.backend.job.domain.SalaryPeriod;
import com.milosz.podsiadly.backend.job.dto.JobOfferSkillDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
public class TheProtocolParser {

    private final ObjectMapper om;

    public Parsed parseFromApiJson(String detailsUrl, String offerId, String json) {
        try {
            JsonNode offer = om.readTree(json);

            String externalId = textOrNull(offer.path("id"));
            if (externalId == null) externalId = offerId;

            String title = textOrNull(offer.path("attributes").path("title").path("value"));
            String company = textOrNull(offer.path("attributes").path("employer").path("name"));

            JsonNode wp0 = firstArrayItem(offer.path("attributes").path("workplaces"));
            String city = textOrNull(wp0.path("city"));
            if (city == null) {
                String loc = textOrNull(wp0.path("location"));
                city = parseCityFromLocation(loc);
            }

            JobLevel level = parseLevel(offer);

            Boolean remote = parseRemoteBoolean(offer);

            SalaryContracts sc = parseSalaryAndContracts(offer);

            String description = buildDescriptionFromTextSections(offer);

            String applyUrl = textOrNull(offer.path("attributes").path("applying").path("applyFormUrlSegment"));

            Instant publishedAt = parsePublishedAt(offer);
            Boolean active = parseActive(offer);

            return new Parsed(
                    externalId,
                    title,
                    description,
                    company,
                    city,
                    remote,
                    level,
                    sc.mainContract,
                    sc.contracts,
                    sc.salaryMin,
                    sc.salaryMax,
                    sc.currency,
                    sc.period,
                    detailsUrl,
                    applyUrl,
                    sc.techTags,
                    sc.techStack,
                    publishedAt,
                    active
            );
        } catch (Exception e) {
            throw new IllegalStateException("THEPROTOCOL parse API JSON error id=" + offerId + " url=" + detailsUrl, e);
        }
    }

    public record Parsed(
            String externalId,
            String title,
            String description,
            String companyName,
            String cityName,
            Boolean remote,
            JobLevel level,
            ContractType mainContract,
            Set<ContractType> contracts,
            Integer salaryMin,
            Integer salaryMax,
            String currency,
            SalaryPeriod salaryPeriod,
            String detailsUrl,
            String applyUrl,
            List<String> techTags,
            List<JobOfferSkillDto> techStack,
            Instant publishedAt,
            Boolean active
    ) {}

    private static JsonNode firstArrayItem(JsonNode arr) {
        return (arr != null && arr.isArray() && arr.size() > 0) ? arr.get(0) : arr;
    }

    private static String textOrNull(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        String t = n.asText(null);
        if (t == null) return null;
        t = t.trim();
        return t.isEmpty() ? null : t;
    }

    private static String parseCityFromLocation(String loc) {
        if (loc == null) return null;
        String[] parts = loc.split(",");
        return parts.length > 0 ? parts[0].trim() : loc.trim();
    }

    private static JobLevel parseLevel(JsonNode offer) {
        JsonNode lvlArr = offer.path("attributes").path("employment").path("positionLevelIds");
        if (lvlArr.isArray() && lvlArr.size() > 0) {
            String v = lvlArr.get(0).asText("").toLowerCase(Locale.ROOT);
            if (v.contains("junior")) return JobLevel.JUNIOR;
            if (v.contains("mid") || v.contains("regular")) return JobLevel.MID;
            if (v.contains("senior")) return JobLevel.SENIOR;
            if (v.contains("lead") || v.contains("principal") || v.contains("staff")) return JobLevel.LEAD;
        }
        return null;
    }

    private static Boolean parseRemoteBoolean(JsonNode offer) {
        JsonNode modes = offer.path("attributes").path("employment").path("detailedWorkModes");
        if (!modes.isArray()) return null;

        boolean hasAny = false;
        boolean hasRemote = false;

        for (JsonNode m : modes) {
            String code = textOrNull(m.path("code"));
            if (code == null) continue;
            hasAny = true;

            String lc = code.toLowerCase(Locale.ROOT);
            if (lc.equals("home-office") || lc.contains("remote")) {
                hasRemote = true;
            }
        }
        return hasAny ? hasRemote : null;
    }

    private static Boolean parseActive(JsonNode offer) {
        JsonNode pub = offer.path("publicationDetails");
        JsonNode active = pub.path("active");
        if (active.isBoolean()) return active.asBoolean();
        JsonNode isActive = pub.path("isActive");
        if (isActive.isBoolean()) return isActive.asBoolean();
        return null;
    }

    private static Instant parsePublishedAt(JsonNode offer) {
        String dt = textOrNull(offer.path("publicationDetails").path("dateOfInitialPublicationUtc"));
        if (dt == null) dt = textOrNull(offer.path("publicationDetails").path("lastPublishedUtc"));
        if (dt == null) return Instant.now();
        try {
            return Instant.parse(dt);
        } catch (Exception ignored) {
            return Instant.now();
        }
    }

    private static final class SalaryContracts {
        Integer salaryMin;
        Integer salaryMax;
        String currency;
        SalaryPeriod period = SalaryPeriod.MONTH;
        Set<ContractType> contracts = EnumSet.noneOf(ContractType.class);
        ContractType mainContract;
        List<String> techTags = List.of();
        List<JobOfferSkillDto> techStack = List.of();
    }

    private static SalaryContracts parseSalaryAndContracts(JsonNode offer) {
        SalaryContracts out = new SalaryContracts();

        JsonNode types = offer.path("attributes").path("employment").path("typesOfContracts");

        ContractCandidate chosen = null;

        if (types.isArray()) {
            for (JsonNode c : types) {
                String name = textOrNull(c.path("name"));
                ContractType ct = mapContract(name);
                if (ct != null) out.contracts.add(ct);

                JsonNode sal = c.path("salary");
                if (chosen == null && sal != null && sal.isObject()) {
                    ContractCandidate cand = parseContractSalaryCandidate(sal);
                    chosen = cand;
                }
            }
        }

        out.mainContract = pickMainContract(out.contracts);

        if (chosen != null) {
            out.salaryMin = chosen.min;
            out.salaryMax = chosen.max;
            out.currency = normalizeCurrency(chosen.currencyCode);
            if (chosen.period != null) out.period = chosen.period;
        }

        // tech tags: expected + optional
        List<String> tags = new ArrayList<>();
        addTechNames(tags, offer.path("technologies").path("expected"));
        addTechNames(tags, offer.path("technologies").path("optional"));

        out.techTags = tags.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .limit(64)
                .toList();

        out.techStack = out.techTags.stream()
                .map(s -> new JobOfferSkillDto(
                        s, null, null,
                        com.milosz.podsiadly.backend.job.domain.SkillSource.STACK
                ))
                .toList();

        return out;
    }

    private static void addTechNames(List<String> out, JsonNode arr) {
        if (arr == null || !arr.isArray()) return;
        for (JsonNode t : arr) {
            String nm = textOrNull(t.path("name"));
            if (nm != null) out.add(nm);
        }
    }

    private static final class ContractCandidate {
        Integer min;
        Integer max;
        String currencyCode;
        SalaryPeriod period;
    }

    private static ContractCandidate parseContractSalaryCandidate(JsonNode sal) {
        ContractCandidate c = new ContractCandidate();
        c.min = parseIntSafe(textOrNull(sal.path("from")));
        c.max = parseIntSafe(textOrNull(sal.path("to")));
        c.currencyCode = textOrNull(sal.path("currencyCode"));

        String shortForm = textOrNull(sal.path("timeUnit").path("shortForm"));
        String longForm  = textOrNull(sal.path("timeUnit").path("longForm"));
        Integer timeUnitId = parseIntSafe(textOrNull(sal.path("timeUnitId")));

        c.period = mapPeriod(shortForm, longForm, timeUnitId);
        return c;
    }

    private static Integer parseIntSafe(String s) {
        if (s == null) return null;
        try {
            return (int) Math.round(Double.parseDouble(s.replace(",", ".").trim()));
        } catch (Exception e) {
            return null;
        }
    }

    private static SalaryPeriod mapPeriod(String shortForm, String longForm, Integer timeUnitId) {
        String s = (shortForm != null ? shortForm : "") + " " + (longForm != null ? longForm : "");
        String lc = s.toLowerCase(Locale.ROOT);

        if (lc.contains("godz") || lc.contains("hour")) return SalaryPeriod.HOUR;
        if (lc.contains("dzie") || lc.contains("day")) return SalaryPeriod.DAY;
        if (lc.contains("tyg")  || lc.contains("week")) return SalaryPeriod.WEEK;
        if (lc.contains("mies") || lc.contains("month")) return SalaryPeriod.MONTH;
        if (lc.contains("rok")  || lc.contains("year") || lc.contains("rocz")) return SalaryPeriod.YEAR;

        if (timeUnitId != null && timeUnitId == 1) return SalaryPeriod.HOUR;

        return SalaryPeriod.MONTH;
    }

    private static String normalizeCurrency(String c) {
        if (c == null) return null;
        String x = c.trim().toUpperCase(Locale.ROOT);
        if (x.equals("ZŁ") || x.equals("ZL") || x.equals("PLN")) return "PLN";
        return x;
    }

    private static ContractType mapContract(String name) {
        if (name == null) return null;
        String lc = name.trim().toLowerCase(Locale.ROOT);

        if (lc.contains("b2b")) return ContractType.B2B;
        if (lc.contains("zlecen")) return ContractType.UZ;
        if (lc.contains("dzieł") || lc.contains("dzielo")) return ContractType.UOD;
        if (lc.contains("o prac") || lc.contains("employment") || lc.contains("contract of employment")) return ContractType.UOP;

        return null;
    }

    private static ContractType pickMainContract(Set<ContractType> contracts) {
        if (contracts == null || contracts.isEmpty()) return null;
        if (contracts.contains(ContractType.B2B)) return ContractType.B2B;
        if (contracts.contains(ContractType.UOP)) return ContractType.UOP;
        if (contracts.contains(ContractType.UZ))  return ContractType.UZ;
        if (contracts.contains(ContractType.UOD)) return ContractType.UOD;
        return contracts.iterator().next();
    }

    private static String buildDescriptionFromTextSections(JsonNode offer) {
        JsonNode sections = offer.path("textSections");
        if (!sections.isArray()) return null;

        String about   = joinElements(sections, "about-project");
        String resp    = joinElements(sections, "responsibilities");
        String req     = joinElements(sections, "requirements-expected");
        String opt     = joinElements(sections, "requirements-optional");
        String offered = joinElements(sections, "offered");

        StringBuilder sb = new StringBuilder();
        if (about != null)   sb.append(about).append("\n\n");
        if (resp != null)    sb.append("Responsibilities:\n").append(resp).append("\n\n");
        if (req != null)     sb.append("Requirements:\n").append(req).append("\n\n");
        if (opt != null)     sb.append("Optional:\n").append(opt).append("\n\n");
        if (offered != null) sb.append("What we offer:\n").append(offered).append("\n\n");

        String out = sb.toString().trim();
        return out.isEmpty() ? null : out;
    }

    private static String joinElements(JsonNode sections, String type) {
        List<String> out = new ArrayList<>();
        for (JsonNode s : sections) {
            String t = textOrNull(s.path("type"));
            if (!type.equals(t)) continue;

            JsonNode el = s.path("elements");
            if (el.isArray()) {
                for (JsonNode e : el) {
                    String v = e.asText(null);
                    if (v != null && !v.isBlank()) out.add(v.trim());
                }
            }
        }
        if (out.isEmpty()) return null;
        return String.join("\n", out);
    }
}
