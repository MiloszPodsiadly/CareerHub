package com.milosz.podsiadly.backend.ingest.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class SolidParser {

    private final ObjectMapper objectMapper;

    public SolidParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SolidParsedOffer parseFromApiJson(String url, String externalId, String json) {

        if (json == null || json.isBlank()) {
            log.debug("[solid-parser] empty response for {}", externalId);
            return null;
        }

        String trimmed = json.stripLeading();
        if (trimmed.startsWith("<!DOCTYPE") || trimmed.startsWith("<html")) {
            log.debug("[solid-parser] HTML-looking response for {} (skipping)", externalId);
            return null;
        }

        final JsonNode root;
        try {
            root = objectMapper.readTree(trimmed);
        } catch (JsonProcessingException e) {
            String snippet = trimmed.substring(0, Math.min(300, trimmed.length()))
                    .replaceAll("\\s+", " ");
            log.debug(
                    "[solid-parser] cannot parse JSON for {} snippet='{}' msg={}",
                    externalId, snippet, e.getOriginalMessage()
            );
            return null;
        }

        JsonNode details = root.path("jobOfferDetails");
        if (details.isMissingNode() || details.isNull()) {
            log.debug("[solid-parser] no jobOfferDetails node for {}, skipping", externalId);
            return null;
        }

        String title    = details.path("jobTitle").asText(null);
        String company  = details.path("companyName").asText(null);
        String division = details.path("division").asText(null);

        String city = extractCity(details);
        String location = appendRemote(details, city);

        String descriptionHtml = combineHtml(
                details.path("jobDescription").asText(""),
                details.path("candidateProfile").asText("")
        );

        String salaryText = buildSalaryText(details);

        List<String> skills = extractSkills(details);

        return SolidParsedOffer.builder()
                .sourceUrl(url)
                .externalId(externalId)
                .title(title)
                .company(company)
                .location(location)
                .division(division)
                .salaryText(salaryText)
                .descriptionHtml(descriptionHtml)
                .skills(skills)
                .build();
    }

    private String extractCity(JsonNode details) {
        JsonNode locs = details.path("locations");
        if (locs.isArray() && locs.size() > 0) {
            String c = locs.get(0).path("city").asText(null);
            if (c != null && !c.isBlank()) return c;
        }
        return details.path("companyCity").asText(null);
    }

    private String appendRemote(JsonNode details, String city) {
        String remote = details.path("remotePossible").asText(null);

        String remoteLabel = switch (remote) {
            case "W całości" -> "100% zdalnie";
            case "Częściowo" -> "częściowo zdalnie";
            default -> null;
        };

        if (remoteLabel == null) return city;

        if (city == null || city.isBlank()) return remoteLabel;
        return city + " / " + remoteLabel;
    }

    private List<String> extractSkills(JsonNode details) {
        List<String> skills = new ArrayList<>();
        JsonNode req = details.path("requiredSkills");

        if (!req.isArray()) return skills;

        for (JsonNode s : req) {
            String n = s.path("name").asText(null);
            if (n != null && !n.isBlank()) skills.add(n.trim());
        }
        return skills;
    }

    private String combineHtml(String a, String b) {
        if ((a == null || a.isBlank()) && (b == null || b.isBlank())) return "";
        if (a == null || a.isBlank()) return b;
        if (b == null || b.isBlank()) return a;
        return a + "<hr/>" + b;
    }

    private String buildSalaryText(JsonNode details) {

        JsonNode normalized = details.path("normalizedSalaryRange");
        JsonNode primary    = details.path("salaryRange");

        JsonNode src = (!normalized.isMissingNode() && !normalized.isNull())
                ? normalized
                : primary;

        if (src == null || src.isMissingNode() || src.isNull()) return null;

        double lo = src.path("lowerBound").asDouble(0);
        double hi = src.path("upperBound").asDouble(0);
        String curr = src.path("currency").asText("PLN");
        String emp  = src.path("employmentType").asText(null);
        String per  = src.path("salaryPeriod").asText(null);

        if (lo <= 0 && hi <= 0) return null;

        String base = (hi > lo)
                ? String.format(Locale.ROOT, "%.0f–%.0f %s", lo, hi, curr)
                : String.format(Locale.ROOT, "%.0f %s", lo, curr);

        StringBuilder sb = new StringBuilder(base);

        if (per != null && !per.isBlank()) {
            sb.append(" / ").append(per.toLowerCase(Locale.ROOT));
        }
        if (emp != null && !emp.isBlank()) {
            sb.append(" (").append(emp).append(")");
        }
        return sb.toString();
    }

    @Value
    @Builder
    public static class SolidParsedOffer {
        String sourceUrl;
        String externalId;
        String title;
        String company;
        String location;
        String division;
        String salaryText;
        String descriptionHtml;
        List<String> skills;
    }
}
