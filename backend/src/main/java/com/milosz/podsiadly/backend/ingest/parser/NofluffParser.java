package com.milosz.podsiadly.backend.ingest.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.milosz.podsiadly.backend.ingest.dto.NofluffJobDto;
import com.milosz.podsiadly.backend.job.domain.ContractType;
import com.milosz.podsiadly.backend.job.domain.JobLevel;
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

            String title = textOrNull(root.path("title"));

            String companyName = textOrNull(root.path("company").path("name"));

            String cityName = null;
            JsonNode places = root.path("location").path("places");
            if (places.isArray() && places.size() > 0) {
                cityName = textOrNull(places.get(0).path("city"));
            }

            Boolean remote = null;
            JsonNode remoteNode = root.path("location").path("remote");
            if (remoteNode.isInt()) {
                int r = remoteNode.asInt();
                if (r == 0) remote = false;
                else remote = true;
            }

            JobLevel level = null;
            JsonNode basics = root.path("basics");
            JsonNode seniorityArr = basics.path("seniority");
            String seniority = null;
            if (seniorityArr.isArray() && seniorityArr.size() > 0) {
                seniority = seniorityArr.get(0).asText(null);
            }
            if (seniority != null) {
                String lc = seniority.toLowerCase(Locale.ROOT);
                if (lc.contains("junior")) level = JobLevel.JUNIOR;
                else if (lc.contains("mid") || lc.contains("regular")) level = JobLevel.MID;
                else if (lc.contains("senior") || lc.contains("expert")) level = JobLevel.SENIOR;
            }

            Integer salaryMin = null;
            Integer salaryMax = null;
            String currency = null;
            Set<ContractType> contracts = EnumSet.noneOf(ContractType.class);
            ContractType mainContract = null;

            JsonNode originalSalary = root.path("essentials").path("originalSalary");
            if (originalSalary.isObject()) {
                currency = textOrNull(originalSalary.path("currency"));

                JsonNode types = originalSalary.path("types");
                if (types.isObject()) {
                    JsonNode chosenTypeNode = null;
                    String chosenKey = null;

                    if (types.has("b2b")) {
                        chosenKey = "b2b";
                        chosenTypeNode = types.get("b2b");
                    } else {
                        Iterator<String> fieldNames = types.fieldNames();
                        if (fieldNames.hasNext()) {
                            chosenKey = fieldNames.next();
                            chosenTypeNode = types.get(chosenKey);
                        }
                    }

                    if (chosenTypeNode != null && chosenTypeNode.isObject()) {
                        JsonNode range = chosenTypeNode.path("range");
                        if (range.isArray() && range.size() >= 2) {
                            salaryMin = (int) Math.round(range.get(0).asDouble());
                            salaryMax = (int) Math.round(range.get(1).asDouble());
                        }
                    }

                    Iterator<String> keys = types.fieldNames();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        ContractType ct = mapContract(k);
                        if (ct != null) contracts.add(ct);
                    }
                }
            }
            if (contracts.isEmpty()) {
                contracts = Set.of();
            } else {
                mainContract = contracts.iterator().next();
            }

            List<String> techTags = new ArrayList<>();
            List<JobOfferSkillDto> techStack = new ArrayList<>();

            JsonNode req = root.path("requirements");

            JsonNode musts = req.path("musts");
            if (musts.isArray()) {
                for (JsonNode m : musts) {
                    String val = textOrNull(m.path("value"));
                    if (val == null) continue;
                    techTags.add(val);
                    techStack.add(new JobOfferSkillDto(
                            val,
                            null,
                            null,
                            SkillSource.REQUIRED
                    ));
                }
            }

            JsonNode nices = req.path("nices");
            if (nices.isArray()) {
                for (JsonNode n : nices) {
                    String val = textOrNull(n.path("value"));
                    if (val == null) continue;
                    techTags.add(val);
                    techStack.add(new JobOfferSkillDto(
                            val,
                            null,
                            null,
                            SkillSource.NICE_TO_HAVE
                    ));
                }
            }

            techTags = techTags.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .limit(64)
                    .toList();

            String description = textOrNull(root.path("details").path("description"));

            Instant publishedAt = parsePublishedAt(root);

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
                    originalUrl,
                    originalUrl,
                    techTags,
                    techStack,
                    publishedAt
            );
        } catch (Exception e) {
            throw new IllegalStateException("NFJ JSON parse error for " + externalId, e);
        }
    }


    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String t = node.asText(null);
        return (t != null && !t.isBlank()) ? t.trim() : null;
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
        String[] candidates = { "posted", "publishedAt", "createdAt", "date" };

        for (String field : candidates) {
            JsonNode n = root.path(field);
            if (n == null || n.isMissingNode() || n.isNull()) continue;

            if (n.isNumber()) {
                long v = n.asLong();
                if (v > 3_000_000_000L) {
                    return Instant.ofEpochMilli(v);
                } else {
                    return Instant.ofEpochSecond(v);
                }
            }

            String txt = n.asText(null);
            if (txt == null || txt.isBlank()) continue;
            try {
                return OffsetDateTime.parse(txt).toInstant();
            } catch (DateTimeParseException ignored) {
            }
        }
        return Instant.now();
    }
}
