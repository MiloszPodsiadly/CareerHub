package com.milosz.podsiadly.backend.ingest.parser;

import com.milosz.podsiadly.backend.job.domain.ContractType;
import com.milosz.podsiadly.backend.job.domain.JobLevel;
import com.milosz.podsiadly.backend.job.domain.SalaryPeriod;
import com.milosz.podsiadly.backend.job.dto.JobOfferSkillDto;
import com.milosz.podsiadly.backend.job.service.SalaryNormalizer;
import com.milosz.podsiadly.backend.job.service.ingest.ExternalJobOfferData;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SolidOfferMapper {

    private SolidOfferMapper() {}

    private static final Pattern SALARY_PATTERN =
            Pattern.compile("(\\d[\\d\\s\\.]*)\\s*([kK])?(?:\\s*[-–]\\s*(\\d[\\d\\s\\.]*)\\s*([kK])?)?");

    private static final Pattern PERIOD_HINT =
            Pattern.compile("(?i)\\b(per\\s*hour|hourly|/h|\\bgodz\\b|godzin(?:a|y|ę)|" +
                    "per\\s*day|daily|/d|\\bdzień\\b|dzien|dniówka|" +
                    "per\\s*week|weekly|/w|\\btydz\\b|tygodniowo|" +
                    "per\\s*month|monthly|/m|\\bmies\\b|miesięcznie|miesiecznie|" +
                    "per\\s*year|yearly|annual|/y|\\brok\\b|rocznie)\\b");

    public static ExternalJobOfferData map(SolidParser.SolidParsedOffer p) {
        String location = p != null ? p.getLocation() : null;

        String  city   = extractCity(location);
        Boolean remote = extractRemote(location);

        Salary salary = parseSalary(p != null ? p.getSalaryText() : null);

        SalaryPeriod period = detectSalaryPeriod(p != null ? p.getSalaryText() : null);
        if (period == null) period = SalaryPeriod.MONTH;

        JobLevel level = detectLevel(p != null ? p.getTitle() : null);

        Set<ContractType> contracts = detectContracts(p != null ? p.getSalaryText() : null);
        ContractType mainContract = pickPreferredContract(contracts);

        String detailsUrl = p != null ? p.getSourceUrl() : null;

        return new ExternalJobOfferData(
                p != null ? p.getTitle() : null,
                p != null ? p.getDescriptionHtml() : null,
                p != null ? p.getCompany() : null,
                city,
                remote,
                level,
                mainContract,
                contracts,
                salary.min(),
                salary.max(),
                salary.currency(),
                period,
                detailsUrl,
                detailsUrl,
                p != null ? safeList(p.getSkills()) : List.of(),
                List.<JobOfferSkillDto>of(),
                Instant.now(),
                Boolean.TRUE
        );
    }

    private static String extractCity(String loc) {
        if (loc == null) return null;
        String t = loc.trim();
        if (t.isEmpty()) return null;
        String lower = t.toLowerCase(Locale.ROOT);

        if (lower.equals("remote") || lower.contains("zdalnie")) return null;

        if (t.contains("/")) {
            String left = t.split("/")[0].trim();
            if (left.isEmpty()) return null;
            if (left.equalsIgnoreCase("remote") || left.toLowerCase(Locale.ROOT).contains("zdalnie")) return null;
            return left;
        }

        t = t.replaceAll("(?i)\\(\\s*remote\\s*\\)", "").trim();
        if (t.isEmpty()) return null;
        if (t.equalsIgnoreCase("poland") || t.equalsIgnoreCase("polska")) return null;

        return t;
    }

    private static Boolean extractRemote(String loc) {
        if (loc == null) return null;
        String lower = loc.toLowerCase(Locale.ROOT);

        boolean remote =
                lower.contains("remote") ||
                        lower.contains("zdalnie") ||
                        lower.contains("hybryd") ||
                        lower.contains("hybrid");

        return remote ? Boolean.TRUE : Boolean.FALSE;
    }

    private record Salary(Integer min, Integer max, String currency) {}

    private static Salary parseSalary(String txt) {
        if (txt == null || txt.isBlank()) {
            return new Salary(null, null, null);
        }

        String norm = txt.replace(",", ".");
        Matcher m = SALARY_PATTERN.matcher(norm);

        Integer min = null;
        Integer max = null;

        if (m.find()) {
            min = parseNum(m.group(1), m.group(2));
            if (m.group(3) != null) {
                max = parseNum(m.group(3), m.group(4));
            } else {
                max = min;
            }
        }

        String up = txt.toUpperCase(Locale.ROOT);
        String currency = null;
        if (up.contains("PLN")) currency = "PLN";
        else if (up.contains("EUR")) currency = "EUR";
        else if (up.contains("USD")) currency = "USD";

        return new Salary(min, max, currency);
    }

    private static Integer parseNum(String s, String kFlag) {
        if (s == null) return null;

        String t = s.trim().toLowerCase(Locale.ROOT);
        t = t.replace(" ", "").replace("\u00A0", "");
        t = t.replace(".", ""); // treat dots as thousands separators in many cases

        boolean hasK = (kFlag != null && !kFlag.isBlank());
        if (hasK) {
            String baseRaw = s.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("\u00A0", "");
            baseRaw = baseRaw.replaceAll("[^0-9.]", "");
            if (baseRaw.isBlank()) return null;
            double base = Double.parseDouble(baseRaw);
            return (int) Math.round(base * 1000d);
        }

        t = t.replaceAll("[^0-9]", "");
        if (t.isBlank()) return null;

        return Integer.parseInt(t);
    }

    private static SalaryPeriod detectSalaryPeriod(String salaryText) {
        if (salaryText == null || salaryText.isBlank()) return null;

        Matcher m = PERIOD_HINT.matcher(salaryText);
        if (!m.find()) return null;

        String hit = m.group(1).toLowerCase(Locale.ROOT);

        SalaryPeriod p = SalaryNormalizer.parsePeriodLoose(hit);
        return p != null ? p : null;
    }

    private static JobLevel detectLevel(String title) {
        if (title == null) return null;
        String t = title.toLowerCase(Locale.ROOT);

        if (t.contains("intern") || t.contains("praktyk") || t.contains("trainee")) return JobLevel.INTERNSHIP;
        if (t.contains("junior") || t.contains("jr")) return JobLevel.JUNIOR;
        if (t.contains("mid") || t.contains("middle") || t.contains("regular")) return JobLevel.MID;
        if (t.contains("senior") || t.contains("sr")) return JobLevel.SENIOR;
        if (t.contains("lead") || t.contains("principal") || t.contains("expert") || t.contains("staff")) return JobLevel.LEAD;

        return null;
    }

    private static Set<ContractType> detectContracts(String salaryText) {
        if (salaryText == null || salaryText.isBlank()) return Set.of();

        EnumSet<ContractType> out = EnumSet.noneOf(ContractType.class);
        String t = salaryText.toLowerCase(Locale.ROOT);

        if (t.contains("b2b")) out.add(ContractType.B2B);

        if (t.contains("uop") || t.contains("uop:") ||
                t.contains("umowa o pracę") || t.contains("umowa o prace") ||
                t.contains("employment contract") || t.contains("contract of employment")) {
            out.add(ContractType.UOP);
        }

        if (t.contains("uz") || t.contains("uz:") ||
                t.contains("umowa zlecenie") || t.contains("zlecenie") || t.contains("mandate")) {
            out.add(ContractType.UZ);
        }

        if (t.contains("uod") || t.contains("uod:") ||
                t.contains("umowa o dzieło") || t.contains("umowa o dzielo") ||
                t.contains("specific-task")) {
            out.add(ContractType.UOD);
        }

        return out.isEmpty() ? Set.of() : out;
    }

    private static ContractType pickPreferredContract(Set<ContractType> set) {
        if (set == null || set.isEmpty()) return null;
        for (ContractType pref : List.of(ContractType.B2B, ContractType.UOP, ContractType.UZ, ContractType.UOD)) {
            if (set.contains(pref)) return pref;
        }
        return set.iterator().next();
    }

    private static List<String> safeList(List<String> in) {
        if (in == null || in.isEmpty()) return List.of();
        List<String> out = new ArrayList<>(in.size());
        for (String s : in) if (s != null && !s.isBlank()) out.add(s);
        return out;
    }
}
