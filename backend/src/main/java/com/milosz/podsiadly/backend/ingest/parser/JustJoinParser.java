package com.milosz.podsiadly.backend.ingest.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.milosz.podsiadly.backend.job.domain.JobLevel;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JustJoinParser {

    // ===== Salary (DOM fallback) =====
    private static final Pattern SAL_RANGE =
            Pattern.compile("(\\d[\\d\\s]*)\\s*-\\s*(\\d[\\d\\s]*)\\s*(PLN|EUR|USD)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CURR_FALLBACK =
            Pattern.compile("\\b(PLN|EUR|USD)\\b", Pattern.CASE_INSENSITIVE);

    // Breadcrumb tokens
    private static final Set<String> TECH_TOKENS = Set.of(
            "frontend","backend","fullstack","mobile","devops","qa","q a","test","testing","data","bi","data/bi",
            "security","ai","ml","ai/ml","embedded","others","other",
            "javascript","typescript","react","angular","vue","flutter","java","python","php","c#","csharp",
            "sql","aws","kubernetes","k8s","docker","azure","gcp","spring",".net","net","node","node.js","nodejs",
            "architecture","admin","analytics","erp"
    );

    // Tag section
    private static final Set<String> TAG_SECTION_TITLES = Set.of(
            "Must have","Nice to have","Tech stack",
            "Wymagane","Mile widziane","Stack technologiczny","Technologie"
    );

    private static final Set<String> TAG_BLACKLIST = Set.of(
            "b2b","uop","uod","uz","full remote","hybrid","onsite",
            "per month","month","net","gross","brutto","netto",
            "junior","mid","senior","lead","intern","regular","advanced","nice to have"
    );
    private static final Set<String> LANGUAGE_WORDS = Set.of(
            "english","polish","german","deutsch","niem","french","francuski","italian","hiszpański","spanish"
    );

    private final ObjectMapper om = new ObjectMapper();

    // ===== public =====
    public ParsedOffer parse(String url, String html) {
        Document doc = Jsoup.parse(html, url);

        // --- JSON-LD ---
        JsonNode ld = firstJobPostingJsonLd(doc);
        String title       = text(ld, "title");
        String description = text(ld, "description");
        String company     = text(ld.path("hiringOrganization"), "name");
        String city        = firstCityFromLd(ld);

        Salary sLd         = salaryFromLd(ld);
        Integer min        = sLd != null ? sLd.min : null;
        Integer max        = sLd != null ? sLd.max : null;
        String  currency   = sLd != null ? sLd.currency : null;

        Boolean remote     = parseRemoteFromLd(ld);
        Instant postedAt   = parseIsoInstant(text(ld, "datePosted"));

        // --- fallback: title/company/city ---
        if (blank(title)) {
            Element h1 = doc.selectFirst("h1, h1[class]");
            if (h1 != null) title = h1.text();
        }
        if (blank(title)) title = doc.select("meta[property=og:title]").attr("content");
        if (blank(title)) title = doc.title();
        if (blank(company)) {
            Element compA = doc.selectFirst("a[href*=\"companies=\"]");
            if (compA != null) company = nz(compA.text(), null);
        }
        if (blank(city)) {
            Elements crumbs = doc.select("nav a[href^='/job-offers/']");
            for (Element a : crumbs) {
                String href = a.attr("href");
                if (href.contains("all-locations")) continue;
                String candidate = a.text().trim();
                String lc = candidate.toLowerCase(Locale.ROOT);
                if (!TECH_TOKENS.contains(lc) && !lc.equals("all offers") && candidate.length() <= 30) {
                    city = candidate;
                    break;
                }
            }
        }

        // --- fallback: remote ---
        if (remote == null) {
            String all = doc.text().toLowerCase(Locale.ROOT);
            if (all.contains("remote")) remote = true;
            else if (all.contains("office")) remote = false;
        }

        // --- salary: Next.js payload (employmentTypes) ---
        if (min == null && max == null) {
            Salary sNext = salaryFromNextData(html);
            if (sNext != null) {
                min = sNext.min;
                max = sNext.max;
                if (blank(currency)) currency = sNext.currency;
            }
        }

        // --- salary: visible HTML near label ---
        SalaryTriplet sSalary = findSalaryNearLabel(doc, "Salary");
        SalaryTriplet sB2B    = findSalaryNearLabel(doc, "B2B");
        SalaryTriplet sPL1    = findSalaryNearLabel(doc, "Wynagrodzenie");
        SalaryTriplet sPL2    = findSalaryNearLabel(doc, "Widełki");
        SalaryTriplet s       = firstNonNull(sSalary, sB2B, sPL1, sPL2);
        if (s != null) {
            if (min == null) min = s.min;
            if (max == null) max = s.max;
            if (blank(currency)) currency = s.currency;
        }
        if (min == null && max != null) min = max;
        if (max == null && min != null) max = min;

        // --- level(Junior/Mid/Senior/Lead/Intern) ---
        JobLevel level = levelFromText((title == null ? "" : title) + " " + (description == null ? "" : description));

        // --- TECH: accurate stack + light tags ---
        List<ParsedSkill> techStack = extractTechStack(ld, doc, html);
        List<String> techTags = techStack.stream().map(ParsedSkill::name).distinct().limit(24).toList();

        return new ParsedOffer(
                title,
                company,
                city,
                remote != null ? remote : Boolean.FALSE,
                level,
                min, max, currency,
                techTags,
                techStack,
                postedAt != null ? postedAt : Instant.now(),
                url, "JUSTJOIN", lastPath(url),
                description
        );
    }

    // ===== JSON-LD =====
    private JsonNode firstJobPostingJsonLd(Document doc) {
        for (Element s : doc.select("script[type=application/ld+json]")) {
            try {
                JsonNode n = om.readTree(s.data());
                if (isJobPosting(n)) return asJobPostingNode(n);
                if (n.isArray()) for (JsonNode it : n) if (isJobPosting(it)) return asJobPostingNode(it);
            } catch (Exception ignore) {}
        }
        return om.createObjectNode();
    }
    private static boolean isJobPosting(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return false;
        if ("JobPosting".equalsIgnoreCase(text(n, "@type"))) return true;
        if (n.has("@graph")) for (JsonNode g : n.get("@graph"))
            if ("JobPosting".equalsIgnoreCase(text(g, "@type"))) return true;
        return false;
    }
    private JsonNode asJobPostingNode(JsonNode n) {
        if ("JobPosting".equalsIgnoreCase(text(n, "@type"))) return n;
        if (n.has("@graph")) for (JsonNode g : n.get("@graph"))
            if ("JobPosting".equalsIgnoreCase(text(g, "@type"))) return g;
        return n;
    }

    private static String firstCityFromLd(JsonNode ld) {
        JsonNode jl = ld.get("jobLocation");
        if (jl == null || jl.isNull()) return null;
        if (jl.isArray()) {
            for (JsonNode it : jl) {
                String loc = text(it.path("address"), "addressLocality");
                if (!blank(loc)) return loc;
            }
            return null;
        }
        return text(jl.path("address"), "addressLocality");
    }

    private static final class Salary {
        final Integer min, max; final String currency;
        Salary(Integer min, Integer max, String currency){ this.min=min; this.max=max; this.currency=currency; }
    }
    private static Salary salaryFromLd(JsonNode ld) {
        JsonNode bs = ld.get("baseSalary");
        if (bs == null || bs.isNull()) return null;
        Integer min = null, max = null; String curr = null;
        if (bs.isArray()) {
            for (JsonNode it : bs) {
                JsonNode val = it.get("value");
                if (val != null) {
                    if (val.has("minValue") || val.has("maxValue")) {
                        if (min == null) min = intOrNull(val, "minValue");
                        if (max == null) max = intOrNull(val, "maxValue");
                    } else if (val.canConvertToInt()) {
                        Integer v = val.asInt(); min = min == null ? v : min; max = max == null ? v : max;
                    }
                }
                if (curr == null) curr = optUpper(text(it, "currency"));
            }
        } else {
            JsonNode val = bs.get("value");
            if (val != null) {
                if (val.has("minValue") || val.has("maxValue")) {
                    min = intOrNull(val, "minValue"); max = intOrNull(val, "maxValue");
                } else if (val.canConvertToInt()) {
                    min = max = val.asInt();
                }
            }
            curr = optUpper(text(bs, "currency"));
        }
        if (min == null && max == null && curr == null) return null;
        return new Salary(min, max, curr);
    }

    private static Boolean parseRemoteFromLd(JsonNode ld) {
        String jlt = text(ld, "jobLocationType"); // TELECOMMUTE
        if (!blank(jlt)) return jlt.toUpperCase(Locale.ROOT).contains("TELECOMMUTE");
        String city = firstCityFromLd(ld);
        if (!blank(city) && "remote".equalsIgnoreCase(city)) return true;
        return null;
    }

    // ===== Salary from Next.js payloadu =====
    private Salary salaryFromNextData(String html) {
        String arr = extractNextFieldArray(html, "\"employmentTypes\"");
        if (arr == null) return null;
        try {
            ArrayNode node = (ArrayNode) om.readTree(arr);
            Integer min = null, max = null; String curr = null; String unit = null;

            // preferencja: b2b -> permanent
            List<String> order = List.of("b2b","permanent");
            for (String pref : order) {
                for (JsonNode it : node) {
                    if (!pref.equalsIgnoreCase(text(it, "type"))) continue;
                    Integer f = intOrNull(it, "from");
                    Integer t = intOrNull(it, "to");
                    String c = optUpper(text(it, "currency"));
                    String u = text(it, "unit"); // month/hour
                    if (f != null || t != null) {
                        min = f != null ? f : t;
                        max = t != null ? t : f;
                        curr = blank(c) ? curr : c;
                        unit = u;
                        break;
                    }
                }
                if (min != null || max != null) break;
            }

            // if nothing after preference – first with numbers
            if (min == null && max == null) {
                for (JsonNode it : node) {
                    Integer f = intOrNull(it, "from");
                    Integer t = intOrNull(it, "to");
                    if (f != null || t != null) {
                        min = f != null ? f : t;
                        max = t != null ? t : f;
                        curr = optUpper(text(it, "currency"));
                        unit = text(it, "unit");
                        break;
                    }
                }
            }

            if (min == null && max == null) return null;
            if (min != null && max == null) max = min;
            if (max != null && min == null) min = max;

            // conversion hour → month (JJIT usues ~168 h)
            if ("hour".equalsIgnoreCase(unit)) {
                min = min != null ? min * 168 : null;
                max = max != null ? max * 168 : null;
            }
            if (blank(curr)) curr = "PLN";
            return new Salary(min, max, curr);
        } catch (Exception ignore) {
            return null;
        }
    }

    // ===== Tech stack (accurate) =====
    public record ParsedSkill(String name, String levelLabel, Integer levelValue, String source) {}

    private List<ParsedSkill> extractTechStack(JsonNode ld, Document doc, String html) {
        LinkedHashMap<String, ParsedSkill> out = new LinkedHashMap<>();

        // 1) Next: requiredSkills/niceToHaveSkills (name + level)
        addAll(out, readNextSkills(html, "\"requiredSkills\"", "REQUIRED"));
        addAll(out, readNextSkills(html, "\"niceToHaveSkills\"", "NICE_TO_HAVE"));

        // 2) DOM: section „Tech stack” – use OR w ONE :matchesOwn(...)
        for (Element header : doc.select(
                "*:matchesOwn((?i)^\\s*(Tech\\s*stack|Stack\\s*technologiczny|Technologie)\\s*$)")) {

            Element box = header.parent() != null ? header.parent() : header;
            for (Element h4 : box.select("h4, h4[class*=MuiTypography-]")) {
                String raw = h4.text();
                if (blank(raw)) continue;

                String lvlTxt = nearestLevelWord(h4);
                Integer lvlVal = levelLabelToValue(lvlTxt);
                String name = normalizeTag(raw);

                if (isPlausibleTag(name)) {
                    out.putIfAbsent(name, new ParsedSkill(name, lvlTxt, lvlVal, "STACK"));
                }
            }
        }

        // 3) JSON-LD: skills/knowsAbout/occupationalCategory
        collectLdTagsAsSkills(ld, out, "skills");
        collectLdTagsAsSkills(ld, out, "knowsAbout");
        collectLdTagsAsSkills(ld, out, "occupationalCategory");

        return new ArrayList<>(out.values());
    }

    private void addAll(Map<String, ParsedSkill> out, List<ParsedSkill> list) {
        for (ParsedSkill s : list) out.putIfAbsent(s.name(), s);
    }

    private List<ParsedSkill> readNextSkills(String html, String field, String source) {
        String arrJson = extractNextFieldArray(html, field);
        if (arrJson == null) return List.of();
        List<ParsedSkill> out = new ArrayList<>();
        try {
            ArrayNode node = (ArrayNode) om.readTree(arrJson);
            for (JsonNode it : node) {
                String name = normalizeTag(text(it, "name"));
                Integer lvl = intOrNull(it, "level"); // 1..4
                String lbl = levelValueToLabel(lvl);
                if (!blank(name) && isPlausibleTag(name)) {
                    out.add(new ParsedSkill(name, lbl, levelLabelToValue(lbl), source));
                }
            }
        } catch (Exception ignore) {}
        return out;
    }

    private void collectLdTagsAsSkills(JsonNode ld, Map<String, ParsedSkill> out, String field) {
        if (ld == null || ld.isMissingNode()) return;
        JsonNode n = ld.get(field);
        if (n == null || n.isNull()) return;
        if (n.isArray()) {
            for (JsonNode it : n) {
                String name = it.isTextual() ? it.asText() : (it.has("name") ? it.get("name").asText() : null);
                name = normalizeTag(name);
                if (isPlausibleTag(name)) out.putIfAbsent(name, new ParsedSkill(name, null, null, "LD"));
            }
        } else if (n.isTextual()) {
            for (String part : n.asText().split("[,;/]")) {
                String name = normalizeTag(part);
                if (isPlausibleTag(name)) out.putIfAbsent(name, new ParsedSkill(name, null, null, "LD"));
            }
        } else if (n.has("name")) {
            String name = normalizeTag(n.get("name").asText());
            if (isPlausibleTag(name)) out.putIfAbsent(name, new ParsedSkill(name, null, null, "LD"));
        }
    }

    private String nearestLevelWord(Element base) {
        // Looks for the words "junior/regular/advanced/master/nice to have/B2/C1..." near the technology name
        Element scope = base.parent() != null ? base.parent() : base;
        String t = scope.text().toLowerCase(Locale.ROOT);
        if (t.contains("master")) return "master";
        if (t.contains("advanced")) return "advanced";
        if (t.contains("regular")) return "regular";
        if (t.contains("junior")) return "junior";
        if (t.contains("nice to have")) return "nice to have";
        if (t.contains("c1")) return "C1";
        if (t.contains("b2")) return "B2";
        return null;
    }

    private Integer levelLabelToValue(String lbl) {
        if (lbl == null) return null;
        switch (lbl.toLowerCase(Locale.ROOT)) {
            case "nice to have": return 1;
            case "junior":       return 2;
            case "regular":      return 3;
            case "advanced":     return 4;
            case "master":       return 5;
            default: return null; // np. B2/C1
        }
    }
    private String levelValueToLabel(Integer v) {
        if (v == null) return null;
        return switch (v) {
            case 1 -> "junior";
            case 2 -> "regular";
            case 3 -> "advanced";
            case 4 -> "master";
            default -> null;
        };
    }

    // ===== Salary z DOM =====
    private static final class SalaryTriplet {
        final Integer min, max; final String currency;
        SalaryTriplet(Integer min,Integer max,String currency){this.min=min;this.max=max;this.currency=currency;}
    }
    private SalaryTriplet findSalaryNearLabel(Document doc, String label) {
        for (Element e : doc.select("*:matchesOwn(^\\s*" + Pattern.quote(label) + "\\s*$)")) {
            SalaryTriplet s;
            if ((s = extractSalaryFrom(e)) != null) return s;
            if ((s = extractSalaryFrom(e.parent())) != null) return s;
            if (e.parent() != null) {
                if ((s = extractSalaryFrom(e.parent().previousElementSibling())) != null) return s;
                if ((s = extractSalaryFrom(e.parent().nextElementSibling())) != null) return s;
                if ((s = extractSalaryFrom(e.parent().parent())) != null) return s;
            }
        }
        return null;
    }
    private SalaryTriplet extractSalaryFrom(Element container) {
        if (container == null) return null;
        String txt = container.text();
        Matcher m = SAL_RANGE.matcher(txt);
        if (m.find()) {
            Integer a = parseIntStrip(m.group(1));
            Integer b = parseIntStrip(m.group(2));
            String cur = m.group(3) != null ? m.group(3).toUpperCase(Locale.ROOT) : null;
            if (blank(cur)) {
                Matcher cm = CURR_FALLBACK.matcher(txt);
                if (cm.find()) cur = cm.group(1).toUpperCase(Locale.ROOT);
            }
            if (blank(cur)) cur = "PLN";
            return new SalaryTriplet(a,b,cur);
        }
        return null;
    }

    // ===== utils =====
    private static String text(JsonNode n, String field) {
        return n != null && n.has(field) && !n.get(field).isNull() ? n.get(field).asText(null) : null;
    }
    private static Integer intOrNull(JsonNode n, String field) {
        return (n != null && n.has(field) && n.get(field).isNumber()) ? n.get(field).asInt() : null;
    }
    private static String optUpper(String s){ return blank(s) ? null : s.toUpperCase(Locale.ROOT); }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String nz(String s, String def) { return blank(s) ? def : s; }
    private static Instant parseIsoInstant(String s) {
        if (blank(s)) return null;
        try { return OffsetDateTime.parse(s).toInstant(); }
        catch (DateTimeParseException e) { return null; }
    }
    private static int parseIntStrip(String s) { return Integer.parseInt(s.replaceAll("\\s+", "")); }
    private static String lastPath(String url) {
        int i = url.lastIndexOf('/'); return i >= 0 ? url.substring(i + 1) : url;
    }
    @SafeVarargs private static <T> T firstNonNull(T... vals){ for (T v:vals) if (v!=null) return v; return null; }

    private static JobLevel levelFromText(String txt) {
        if (txt == null) return JobLevel.MID;
        String t = txt.toLowerCase(Locale.ROOT);
        if (t.contains("intern")) return JobLevel.INTERNSHIP;
        if (t.contains("junior") || t.contains("młodszy")) return JobLevel.JUNIOR;
        if (t.contains("senior") || t.contains("starszy")) return JobLevel.SENIOR;
        if (t.contains("lead") || t.contains("principal")) return JobLevel.LEAD;
        return JobLevel.MID;
    }

    // === Next.js field extractor (for arrays) ===
    private String extractNextFieldArray(String html, String fieldNameQuoted) {
        Pattern p = Pattern.compile(fieldNameQuoted + "\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
        Matcher m = p.matcher(html);
        if (!m.find()) return null;
        String captured = m.group(1).trim();
        String json = "[" + unescapeJson(captured) + "]";
        try {
            om.readTree(json);
            return json;
        } catch (Exception e) {
            try {
                json = "[" + captured + "]";
                om.readTree(json);
                return json;
            } catch (Exception ignore) {
                return null;
            }
        }
    }
    private static String unescapeJson(String s) {
        String x = s.replace("\\\"", "\"");
        return x.replace("\\\\", "\\");
    }

    //normalization/tag filters
    private String normalizeTag(String s) {
        if (s == null) return null;
        String t = s.replace('\u00A0',' ').trim().replaceAll("\\s{2,}", " ");
        if (t.isEmpty()) return t;

        if (t.equalsIgnoreCase("js")) t = "JavaScript";
        if (t.equalsIgnoreCase("ts")) t = "TypeScript";
        if (t.matches("(?i)node ?\\.js")) t = "Node.js";
        if (t.matches("(?i)dotnet|\\.net")) t = ".NET";
        if (t.matches("(?i)csharp|c sharp")) t = "C#";
        if (t.matches("(?i)pl/sql")) t = "PL/SQL";

        if (t.length() <= 3) t = t.toUpperCase(Locale.ROOT);
        else if (t.equals(t.toLowerCase(Locale.ROOT))) {
            t = Character.toUpperCase(t.charAt(0)) + t.substring(1);
        }
        return t;
    }
    private boolean isPlausibleTag(String t) {
        if (t == null || t.isBlank()) return false;
        String lc = t.toLowerCase(Locale.ROOT);
        if (LANGUAGE_WORDS.contains(lc)) return false;
        for (String bad : TAG_BLACKLIST) if (lc.contains(bad)) return false;
        if (t.length() > 30) return false;
        if (t.matches(".*\\d{3,}.*")) return false;
        if (t.matches("(?i).*(pln|eur|usd|brutto|netto|gross|net).*")) return false;
        return t.matches(".*[\\p{L}\\p{Nd}#.+].*");
    }

    // ===== parsing result =====
    public record ParsedOffer(
            String title,
            String companyName,
            String cityName,
            Boolean remote,
            JobLevel level,
            Integer min,
            Integer max,
            String currency,
            List<String> techTags,
            List<ParsedSkill> techStack,
            Instant publishedAt,
            String url,
            String source,
            String externalId,
            String description
    ) {}
}
