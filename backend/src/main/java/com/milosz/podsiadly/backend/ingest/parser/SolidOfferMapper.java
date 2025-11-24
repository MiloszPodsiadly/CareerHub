package com.milosz.podsiadly.backend.ingest.parser;

import com.milosz.podsiadly.backend.job.domain.ContractType;
import com.milosz.podsiadly.backend.job.domain.JobLevel;
import com.milosz.podsiadly.backend.job.dto.JobOfferSkillDto;
import com.milosz.podsiadly.backend.job.service.ingest.ExternalJobOfferData;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SolidOfferMapper {

    private static final Pattern SALARY_PATTERN =
            Pattern.compile("(\\d[\\d\\s\\.]*)[kK]?(?:\\s*[-–]\\s*(\\d[\\d\\s\\.]*))?");

    public static ExternalJobOfferData map(SolidParser.SolidParsedOffer p) {
        String location = p.getLocation();

        String  city        = extractCity(location);
        Boolean remote      = extractRemote(location);

        Salary            salary      = parseSalary(p.getSalaryText());
        JobLevel          level       = detectLevel(p.getTitle());
        Set<ContractType> contracts   = detectContracts(p.getSalaryText());
        ContractType      mainContract = contracts.stream().findFirst().orElse(null);

        return new ExternalJobOfferData(
                p.getTitle(),
                p.getDescriptionHtml(),
                p.getCompany(),
                city,
                remote,
                level,
                mainContract,
                contracts,
                salary.min(),
                salary.max(),
                salary.currency(),
                p.getSourceUrl(),
                p.getSourceUrl(),
                p.getSkills(),
                List.<JobOfferSkillDto>of(),
                Instant.now(),
                true
        );
    }


    private static String extractCity(String loc) {
        if (loc == null) return null;
        if (loc.contains("/")) {
            return loc.split("/")[0].trim();
        }
        return loc.trim();
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
            min = parseNum(m.group(1));
            if (m.group(2) != null) {
                max = parseNum(m.group(2));
            } else {
                max = min;
            }
        }

        String currency = null;
        if (txt.toUpperCase(Locale.ROOT).contains("PLN")) {
            currency = "PLN";
        }

        return new Salary(min, max, currency);
    }

    private static Integer parseNum(String s) {
        if (s == null) return null;
        s = s.trim().toLowerCase(Locale.ROOT);
        s = s.replace(" ", "").replace("\u00A0", "");
        s = s.replace(".", "");

        if (s.endsWith("k")) {
            s = s.substring(0, s.length() - 1);
            double base = Double.parseDouble(s);
            return (int) Math.round(base * 1000);
        }
        return (int) Math.round(Double.parseDouble(s));
    }


    private static JobLevel detectLevel(String title) {
        if (title == null) return null;
        String t = title.toLowerCase(Locale.ROOT);

        if (t.contains("intern") || t.contains("praktyk") || t.contains("trainee")) return JobLevel.INTERNSHIP;
        if (t.contains("junior"))   return JobLevel.JUNIOR;
        if (t.contains("mid") || t.contains("middle") || t.contains("regular")) return JobLevel.MID;
        if (t.contains("senior") || t.contains("sr")) return JobLevel.SENIOR;
        if (t.contains("lead") || t.contains("principal") || t.contains("expert")) return JobLevel.LEAD;

        return null;
    }

    private static Set<ContractType> detectContracts(String salaryText) {
        if (salaryText == null) return Set.of();

        EnumSet<ContractType> out = EnumSet.noneOf(ContractType.class);
        String t = salaryText.toLowerCase(Locale.ROOT);

        if (t.contains("b2b")) out.add(ContractType.B2B);
        if (t.contains("uop") || t.contains("umowa o pracę") || t.contains("umowa o prace")) {
            out.add(ContractType.UOP);
        }
        if (t.contains("uz:") || t.contains("umowa zlecenie")) {
            out.add(ContractType.UZ);
        }

        return out;
    }
}
