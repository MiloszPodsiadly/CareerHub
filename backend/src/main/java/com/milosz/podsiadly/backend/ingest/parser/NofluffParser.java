package com.milosz.podsiadly.backend.ingest.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.milosz.podsiadly.backend.ingest.dto.NofluffJobDto;
import com.milosz.podsiadly.backend.job.domain.ContractType;
import com.milosz.podsiadly.backend.job.domain.JobLevel;
import com.milosz.podsiadly.backend.job.domain.SalaryPeriod;
import com.milosz.podsiadly.backend.job.domain.SkillSource;
import com.milosz.podsiadly.backend.job.dto.JobOfferSkillDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

@Component
@RequiredArgsConstructor
public class NofluffParser {

    private final ObjectMapper om;

    public NofluffJobDto parseFromApiJson(String externalId, String json, String originalUrl) {
        try {
            JsonNode root = om.readTree(json);
            JsonNode job = resolveJobNode(root, externalId);
            String title = textOrNull(job.path("title"));
            if (title == null) title = textOrNull(job.path("name")); // czasem inaczej

            String companyName =
                    textOrNull(job.path("company").path("name")) != null
                            ? textOrNull(job.path("company").path("name"))
                            : textOrNull(job.path("name")); // w postings jest "name" jako firma

            String cityName = firstCity(job);
            Boolean remote = parseRemote(job);
            JobLevel level = parseLevel(job);
            SalaryParse sal = parseSalary(job);
            Integer salaryMin = sal.min;
            Integer salaryMax = sal.max;
            String currency = sal.currency;
            SalaryPeriod salaryPeriod = sal.period;
            Set<ContractType> contracts = sal.contracts != null ? sal.contracts : Set.of();
            ContractType mainContract = pickMainContract(contracts);
            SkillsParse skills = parseSkills(job);
            List<String> techTags = skills.tags.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(NofluffParser::clip64)
                    .distinct()
                    .limit(64)
                    .toList();

            String description =
                    textOrNull(job.path("details").path("description")) != null
                            ? textOrNull(job.path("details").path("description"))
                            : textOrNull(job.path("description"));

            Instant publishedAt = parsePublishedAt(job);

            String detailsUrl = originalUrl;
            String applyUrl = originalUrl;

            return new NofluffJobDto(
                    externalId,
                    title,
                    description,
                    companyName,
                    cityName,
                    remote,
                    level,
                    mainContract,
                    contracts,
                    salaryMin,
                    salaryMax,
                    currency,
                    salaryPeriod,
                    detailsUrl,
                    applyUrl,
                    techTags,
                    skills.stack,
                    publishedAt,
                    true
            );
        } catch (Exception e) {
            throw new IllegalStateException("NFJ JSON parse error for " + externalId, e);
        }
    }

    private static JsonNode resolveJobNode(JsonNode root, String externalId) {
        if (root == null || root.isMissingNode() || root.isNull()) return root;

        JsonNode postings = root.path("postings");
        if (postings.isArray() && postings.size() > 0) {
            for (JsonNode p : postings) {
                String id = textOrNull(p.path("id"));
                if (id != null && id.equalsIgnoreCase(externalId)) return p;
            }
            return postings.get(0);
        }
        return root;
    }

    private static String firstCity(JsonNode job) {
        JsonNode places = job.path("location").path("places");
        if (places.isArray() && places.size() > 0) {
            String c = textOrNull(places.get(0).path("city"));
            if (c != null) return c;
        }
        return textOrNull(job.path("city"));
    }

    private static Boolean parseRemote(JsonNode job) {
        JsonNode fr = job.path("fullyRemote");
        if (fr.isBoolean()) return fr.asBoolean();
        JsonNode lfr = job.path("location").path("fullyRemote");
        if (lfr.isBoolean()) return lfr.asBoolean();
        JsonNode remoteNode = job.path("location").path("remote");
        if (remoteNode.isInt()) return remoteNode.asInt() != 0;
        if (remoteNode.isBoolean()) return remoteNode.asBoolean();

        return null;
    }

    private static JobLevel parseLevel(JsonNode job) {
        JsonNode seniorityArr = job.path("basics").path("seniority");
        String seniority = null;
        if (seniorityArr.isArray() && seniorityArr.size() > 0) {
            seniority = seniorityArr.get(0).asText(null);
        }

        if (seniority == null) {
            JsonNode s2 = job.path("seniority");
            if (s2.isArray() && s2.size() > 0) seniority = s2.get(0).asText(null);
        }

        if (seniority == null) return null;

        String lc = seniority.toLowerCase(Locale.ROOT);
        if (lc.contains("junior")) return JobLevel.JUNIOR;
        if (lc.contains("mid") || lc.contains("regular")) return JobLevel.MID;
        if (lc.contains("senior") || lc.contains("expert")) return JobLevel.SENIOR;
        if (lc.contains("lead") || lc.contains("principal") || lc.contains("staff")) return JobLevel.LEAD;

        return null;
    }

    private static final class SalaryParse {
        Integer min;
        Integer max;
        String currency;
        SalaryPeriod period;
        Set<ContractType> contracts;
    }

    private SalaryParse parseSalary(JsonNode job) {
        SalaryParse out = new SalaryParse();
        out.contracts = EnumSet.noneOf(ContractType.class);

        JsonNode originalSalary = job.path("essentials").path("originalSalary");
        if (originalSalary.isObject()) {
            out.currency = textOrNull(originalSalary.path("currency"));
            out.period = parseSalaryPeriod(originalSalary);

            JsonNode types = originalSalary.path("types");
            if (types.isObject()) {
                JsonNode chosenTypeNode = null;
                if (types.has("b2b")) {
                    out.contracts.add(ContractType.B2B);
                    chosenTypeNode = types.get("b2b");
                } else {
                    Iterator<String> fieldNames = types.fieldNames();
                    if (fieldNames.hasNext()) {
                        String chosenKey = fieldNames.next();
                        ContractType ct = mapContract(chosenKey);
                        if (ct != null) out.contracts.add(ct);
                        chosenTypeNode = types.get(chosenKey);
                    }
                }

                if (chosenTypeNode != null && chosenTypeNode.isObject()) {
                    JsonNode range = chosenTypeNode.path("range");
                    if (range.isArray() && range.size() >= 2) {
                        out.min = (int) Math.round(range.get(0).asDouble());
                        out.max = (int) Math.round(range.get(1).asDouble());
                    }
                    SalaryPeriod p2 = parseSalaryPeriod(chosenTypeNode);
                    if (p2 != null) out.period = p2;
                }
                Iterator<String> keys = types.fieldNames();
                while (keys.hasNext()) {
                    String k = keys.next();
                    ContractType ct = mapContract(k);
                    if (ct != null) out.contracts.add(ct);
                }
            }
        }

        if ((out.min == null && out.max == null) || out.currency == null) {
            JsonNode sal = job.path("salary");
            if (sal.isObject()) {
                Double from = sal.path("from").isNumber() ? sal.path("from").asDouble() : null;
                Double to   = sal.path("to").isNumber() ? sal.path("to").asDouble() : null;

                if (from != null) out.min = (int) Math.round(from);
                if (to != null) out.max = (int) Math.round(to);

                String cur = textOrNull(sal.path("currency"));
                if (cur != null) out.currency = cur;

                String type = textOrNull(sal.path("type"));
                ContractType ct = mapContract(type);
                if (ct != null) out.contracts.add(ct);

                SalaryPeriod p = parseSalaryPeriod(sal);
                if (p != null) out.period = p;
            }
        }

        if (out.period == null) out.period = SalaryPeriod.MONTH;
        if (out.contracts == null || out.contracts.isEmpty()) out.contracts = Set.of();
        else out.contracts = EnumSet.copyOf(out.contracts);

        return out;
    }

    private static SalaryPeriod parseSalaryPeriod(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;

        String[] fields = { "period", "salaryPeriod", "timeUnit", "unit", "interval" };

        for (String f : fields) {
            JsonNode n = node.path(f);
            if (n == null || n.isMissingNode() || n.isNull()) continue;
            String v = n.asText(null);
            SalaryPeriod p = mapPeriodToken(v);
            if (p != null) return p;
        }

        return null;
    }

    private static SalaryPeriod mapPeriodToken(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String t = raw.trim().toLowerCase(Locale.ROOT);

        if (t.contains("hour") || t.equals("h")) return SalaryPeriod.HOUR;
        if (t.contains("day")  || t.contains("daily")) return SalaryPeriod.DAY;
        if (t.contains("week") || t.contains("weekly")) return SalaryPeriod.WEEK;
        if (t.contains("month") || t.contains("monthly")) return SalaryPeriod.MONTH;
        if (t.contains("year") || t.contains("annual") || t.contains("yearly")) return SalaryPeriod.YEAR;
        if ("month".equals(t) || "m".equals(t)) return SalaryPeriod.MONTH;
        if ("year".equals(t) || "y".equals(t)) return SalaryPeriod.YEAR;

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

    private static final class SkillsParse {
        List<String> tags = new ArrayList<>();
        List<JobOfferSkillDto> stack = new ArrayList<>();
    }

    private SkillsParse parseSkills(JsonNode job) {
        SkillsParse out = new SkillsParse();
        JsonNode req = job.path("requirements");
        JsonNode musts = req.path("musts");
        if (musts.isArray()) {
            for (JsonNode m : musts) {
                String val = textOrNull(m.path("value"));
                if (val == null) continue;
                out.tags.add(val);
                out.stack.add(new JobOfferSkillDto(val, null, null, SkillSource.REQUIRED));
            }
        }

        JsonNode nices = req.path("nices");
        if (nices.isArray()) {
            for (JsonNode n : nices) {
                String val = textOrNull(n.path("value"));
                if (val == null) continue;
                out.tags.add(val);
                out.stack.add(new JobOfferSkillDto(val, null, null, SkillSource.NICE_TO_HAVE));
            }
        }

        if (!out.stack.isEmpty()) return out;
        JsonNode tiles = job.path("tiles").path("values");
        if (tiles.isArray()) {
            for (JsonNode t : tiles) {
                String value = textOrNull(t.path("value"));
                String type = textOrNull(t.path("type"));
                if (value == null) continue;

                SkillSource src = "requirement".equalsIgnoreCase(type)
                        ? SkillSource.REQUIRED
                        : SkillSource.STACK;

                out.tags.add(value);
                out.stack.add(new JobOfferSkillDto(value, null, null, src));
            }
        }

        return out;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String t = node.asText(null);
        return (t != null && !t.isBlank()) ? t.trim() : null;
    }

    private static String clip64(String s) {
        if (s == null) return null;
        String x = s.trim();
        if (x.length() <= 64) return x;
        return x.substring(0, 64);
    }

    private static ContractType mapContract(String code) {
        if (code == null) return null;
        String lc = code.toLowerCase(Locale.ROOT);

        return switch (lc) {
            case "b2b" -> ContractType.B2B;
            case "permanent", "employment_contract", "uop", "full_time" -> ContractType.UOP;
            case "mandate", "umowa_zlecenie", "uz" -> ContractType.UZ;
            case "specific_task", "umowa_o_dzielo", "uod" -> ContractType.UOD;
            default -> null;
        };
    }

    private static Instant parsePublishedAt(JsonNode root) {
        String[] candidates = { "posted", "publishedAt", "createdAt", "date", "renewed" };

        for (String field : candidates) {
            JsonNode n = root.path(field);
            if (n == null || n.isMissingNode() || n.isNull()) continue;

            if (n.isNumber()) {
                long v = n.asLong();
                if (v > 3_000_000_000L) return Instant.ofEpochMilli(v);
                return Instant.ofEpochSecond(v);
            }

            String txt = n.asText(null);
            if (txt == null || txt.isBlank()) continue;
            try {
                return OffsetDateTime.parse(txt).toInstant();
            } catch (DateTimeParseException ignored) {}
        }
        return Instant.now();
    }
}
