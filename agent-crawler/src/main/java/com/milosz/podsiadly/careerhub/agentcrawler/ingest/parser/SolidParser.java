package com.milosz.podsiadly.careerhub.agentcrawler.ingest.parser;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SolidParser {

    private static final Pattern CITY_FIELD =
            Pattern.compile("\"city\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern REQUIRED_SKILL_NAME =
            Pattern.compile("\"name\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

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
            return parseFromMalformedPayload(url, externalId, trimmed);
        }

        JsonNode details = root.path("jobOfferDetails");
        if (details.isMissingNode() || details.isNull()) {
            log.debug("[solid-parser] no jobOfferDetails node for {}, skipping", externalId);
            return null;
        }

        String title = details.path("jobTitle").asText(null);
        String company = details.path("companyName").asText(null);
        String division = details.path("division").asText(null);

        String city = extractCity(details);
        String location = appendRemoteLabel(details.path("remotePossible").asText(null), city);

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

    private SolidParsedOffer parseFromMalformedPayload(String url, String externalId, String payload) {
        String division = extractStringField(payload, "division");
        String title = extractStringField(payload, "jobTitle");
        String company = extractStringField(payload, "companyName");
        String city = extractCityFromPayload(payload);
        String location = appendRemoteLabel(extractStringField(payload, "remotePossible"), city);
        String salaryText = buildSalaryTextFromPayload(payload);
        List<String> skills = extractSkillsFromPayload(payload);

        if (title == null && company == null && division == null && city == null && salaryText == null && skills.isEmpty()) {
            log.debug("[solid-parser] malformed payload fallback failed for {}", externalId);
            return null;
        }

        log.debug("[solid-parser] using fallback field extraction for {}", externalId);

        return SolidParsedOffer.builder()
                .sourceUrl(url)
                .externalId(externalId)
                .title(title)
                .company(company)
                .location(location)
                .division(division)
                .salaryText(salaryText)
                .descriptionHtml("")
                .skills(skills)
                .build();
    }

    private String extractCity(JsonNode details) {
        JsonNode locs = details.path("locations");
        if (locs.isArray() && locs.size() > 0) {
            String city = locs.get(0).path("city").asText(null);
            if (city != null && !city.isBlank()) {
                return city;
            }
        }
        return details.path("companyCity").asText(null);
    }

    private String extractCityFromPayload(String payload) {
        Matcher cityMatcher = CITY_FIELD.matcher(payload);
        if (cityMatcher.find()) {
            String city = unescapeJson(cityMatcher.group(1));
            if (city != null && !city.isBlank()) {
                return city;
            }
        }
        return extractStringField(payload, "companyCity");
    }

    private String appendRemoteLabel(String remote, String city) {
        String remoteLabel = switch (remote) {
            case "W caÅ‚oÅ›ci" -> "100% zdalnie";
            case "CzÄ™Å›ciowo" -> "czÄ™Å›ciowo zdalnie";
            default -> null;
        };

        if (remoteLabel == null) {
            return city;
        }
        if (city == null || city.isBlank()) {
            return remoteLabel;
        }
        return city + " / " + remoteLabel;
    }

    private List<String> extractSkills(JsonNode details) {
        List<String> skills = new ArrayList<>();
        JsonNode req = details.path("requiredSkills");
        if (!req.isArray()) {
            return skills;
        }

        for (JsonNode skill : req) {
            String name = skill.path("name").asText(null);
            if (name != null && !name.isBlank()) {
                skills.add(name.trim());
            }
        }
        return skills;
    }

    private List<String> extractSkillsFromPayload(String payload) {
        List<String> skills = new ArrayList<>();
        Matcher matcher = REQUIRED_SKILL_NAME.matcher(payload);
        while (matcher.find() && skills.size() < 30) {
            String name = unescapeJson(matcher.group(1));
            if (name != null && !name.isBlank()) {
                skills.add(name.trim());
            }
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
        JsonNode primary = details.path("salaryRange");

        JsonNode src = (!normalized.isMissingNode() && !normalized.isNull())
                ? normalized
                : primary;

        if (src == null || src.isMissingNode() || src.isNull()) return null;

        double lo = src.path("lowerBound").asDouble(0);
        double hi = src.path("upperBound").asDouble(0);
        String curr = src.path("currency").asText("PLN");
        String emp = src.path("employmentType").asText(null);
        String per = src.path("salaryPeriod").asText(null);

        if (lo <= 0 && hi <= 0) return null;

        String base = (hi > lo)
                ? String.format(Locale.ROOT, "%.0f-%.0f %s", lo, hi, curr)
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

    private String buildSalaryTextFromPayload(String payload) {
        String lowerBound = extractNumberField(payload, "lowerBound");
        String upperBound = extractNumberField(payload, "upperBound");
        String currency = extractStringField(payload, "currency");
        String employmentType = extractStringField(payload, "employmentType");
        String salaryPeriod = extractStringField(payload, "salaryPeriod");

        if (lowerBound == null && upperBound == null) {
            return null;
        }

        String amount = (lowerBound != null && upperBound != null && !lowerBound.equals(upperBound))
                ? lowerBound + "-" + upperBound
                : firstNonBlank(lowerBound, upperBound);

        if (amount == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder(amount);
        if (currency != null && !currency.isBlank()) {
            sb.append(" ").append(currency);
        }
        if (salaryPeriod != null && !salaryPeriod.isBlank()) {
            sb.append(" / ").append(salaryPeriod.toLowerCase(Locale.ROOT));
        }
        if (employmentType != null && !employmentType.isBlank()) {
            sb.append(" (").append(employmentType).append(")");
        }
        return sb.toString();
    }

    private String extractStringField(String payload, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher matcher = pattern.matcher(payload);
        if (!matcher.find()) {
            return null;
        }
        return unescapeJson(matcher.group(1));
    }

    private String extractNumberField(String payload, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
        Matcher matcher = pattern.matcher(payload);
        if (!matcher.find()) {
            return null;
        }

        String raw = matcher.group(1);
        if (raw.endsWith(".0")) {
            return raw.substring(0, raw.length() - 2);
        }
        return raw;
    }

    private String unescapeJson(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readValue("\"" + raw + "\"", String.class);
        } catch (JsonProcessingException ex) {
            return raw
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t");
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
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
