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

    private static final Pattern SAL_RANGE =
            Pattern.compile("(\\d[\\d\\s]*)\\s*-\\s*(\\d[\\d\\s]*)\\s*(PLN|EUR|USD)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CURR_FALLBACK =
            Pattern.compile("\\b(PLN|EUR|USD)\\b", Pattern.CASE_INSENSITIVE);

    private static final Set<String> TECH_TOKENS = Set.of(
            "frontend","backend","fullstack","mobile","devops","qa","q a","test","testing","data","bi","data/bi",
            "security","ai","ml","ai/ml","embedded","others","other",
            "javascript","typescript","react","angular","vue","flutter","java","python","php","c#","csharp",
            "sql","aws","kubernetes","k8s","docker","azure","gcp","spring",".net","net","node","node.js","nodejs",
            "architecture","admin","analytics","erp"
    );

    // ====== Tag sections (DOM) ======
    private static final Set<String> TAG_SECTION_TITLES = Set.of(
            "Must have","Nice to have","Tech stack",
            "Wymagane","Mile widziane","Stack technologiczny","Technologie"
    );

    // ====== Tag blacklist ======
    private static final Set<String> TAG_BLACKLIST = Set.of(
            "b2b","uop","uod","uz","full remote","hybrid","onsite",
            "per month","month","net","gross","brutto","netto",
            "junior","mid","senior","lead","intern","regular","advanced","nice to have"
    );
    private static final Set<String> LANGUAGE_WORDS = Set.of(
            "english","polish","german","deutsch","niem","french","francuski","italian","hiszpański","spanish"
    );

    private static final int RX = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final String LB = "(?<![\\p{L}\\p{N}])";
    private static final String RB = "(?![\\p{L}\\p{N}])";

    private static final Pattern RX_LEAD   = Pattern.compile(LB + "(?:lead|tech\\s*lead|team\\s*lead|principal|staff|head)" + RB, RX);
    private static final Pattern RX_SENIOR = Pattern.compile(LB + "(?:senior|starszy|sr\\.?|sen\\.?)" + RB, RX);
    private static final Pattern RX_MID    = Pattern.compile(LB + "(?:regular|mid(?!dle)|middle|średni\\w*)" + RB, RX);
    private static final Pattern RX_JUNIOR = Pattern.compile(LB + "(?:junior|młodszy|mlodszy|jr\\.?)" + RB, RX);
    private static final Pattern RX_INTERN = Pattern.compile(LB + "(?:intern(?:ship)?|trainee|apprentice|praktyk\\w*|staż\\w*|staz\\w*)" + RB, RX);

    private final ObjectMapper om = new ObjectMapper();

    public ParsedOffer parse(String url, String html) {
        Document doc = Jsoup.parse(html, url);

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

        if (remote == null) {
            String all = doc.text().toLowerCase(Locale.ROOT);
            if (all.contains("remote")) remote = true;
            else if (all.contains("office")) remote = false;
        }

        if (min == null && max == null) {
            Salary sNext = salaryFromNextData(html);
            if (sNext != null) {
                min = sNext.min;
                max = sNext.max;
                if (blank(currency)) currency = sNext.currency;
            }
        }

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

        JobLevel level = firstNonNull(
                levelFromNext(html),
                levelFromChips(doc),
                levelFromTextStrict((nz(title, "") + " " + nz(description, "")))
        );
        if (level == null) level = JobLevel.MID;

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

    private Salary salaryFromNextData(String html) {
        String arr = extractNextFieldArray(html, "\"employmentTypes\"");
        if (arr == null) return null;
        try {
            ArrayNode node = (ArrayNode) om.readTree(arr);
            Integer min = null, max = null; String curr = null; String unit = null;

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

    private String findNextStringAny(String html, String... keys) {
        for (String key : keys) {
            Pattern p = Pattern.compile(key + "\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
            Matcher m = p.matcher(html);
            if (m.find()) return unescapeJson(m.group(1)).trim();
        }
        return null;
    }
    private JobLevel levelFromNext(String html) {
        String s = findNextStringAny(html, "\"experienceLevel\"", "\"seniority\"");
        if (!blank(s)) return mapLevelString(s);

        String arr = extractNextFieldArray(html, "\"experienceLevels\"");
        if (arr != null) {
            try {
                ArrayNode node = (ArrayNode) om.readTree(arr);
                JobLevel best = null;
                for (JsonNode it : node) {
                    if (it.isTextual()) best = pickHigher(best, mapLevelString(it.asText()));
                }
                return best;
            } catch (Exception ignore) {}
        }
        return null;
    }
    private JobLevel levelFromChips(Document doc) {
        Element chip = doc.selectFirst(
                "*:matchesOwn((?i)^\\s*(Intern(ship)?|Trainee|Staż|Praktyk\\w*|Junior|Jr\\.?|Mid(dle)?|Regular|Senior|Sr\\.?|Lead|Principal|Staff|Head)\\s*$)");
        return chip != null ? mapLevelString(chip.text()) : null;
    }
    private JobLevel mapLevelString(String s) {
        if (blank(s)) return null;
        String t = s.trim().toLowerCase(Locale.ROOT);
        if (t.matches("(?i)intern(ship)?|trainee|apprentice|praktyk\\p{L}*|staż\\p{L}*|staz\\p{L}*")) return JobLevel.INTERNSHIP;
        if (t.matches("(?i)jr\\.?|junior|młodsz\\p{L}*|mlodsz\\p{L}*"))   return JobLevel.JUNIOR;
        if (t.matches("(?i)mid(dle)?|regular|średni\\p{L}*"))            return JobLevel.MID;
        if (t.matches("(?i)sr\\.?|senior|starsz\\p{L}*"))                return JobLevel.SENIOR;
        if (t.matches("(?i)lead|principal|staff|head|tech\\s*lead"))     return JobLevel.LEAD;
        return null;
    }
    private JobLevel pickHigher(JobLevel a, JobLevel b) {
        if (a == null) return b;
        if (b == null) return a;
        List<JobLevel> order = List.of(JobLevel.INTERNSHIP, JobLevel.JUNIOR, JobLevel.MID, JobLevel.SENIOR, JobLevel.LEAD);
        return order.indexOf(b) > order.indexOf(a) ? b : a;
    }
    private static JobLevel levelFromTextStrict(String txt) {
        if (txt == null || txt.isBlank()) return null;
        String t = txt.toLowerCase(Locale.ROOT);
        if (RX_LEAD.matcher(t).find())   return JobLevel.LEAD;
        if (RX_SENIOR.matcher(t).find()) return JobLevel.SENIOR;
        if (RX_MID.matcher(t).find())    return JobLevel.MID;
        if (RX_JUNIOR.matcher(t).find()) return JobLevel.JUNIOR;
        if (RX_INTERN.matcher(t).find()) return JobLevel.INTERNSHIP;
        return null;
    }

    public record ParsedSkill(String name, String levelLabel, Integer levelValue, String source) {}
    private List<ParsedSkill> extractTechStack(JsonNode ld, Document doc, String html) {
        LinkedHashMap<String, ParsedSkill> out = new LinkedHashMap<>();

        addAll(out, readNextSkills(html, "\"requiredSkills\"", "REQUIRED"));
        addAll(out, readNextSkills(html, "\"niceToHaveSkills\"", "NICE_TO_HAVE"));

        for (Element header : doc.select("*:matchesOwn((?i)^\\s*(Tech\\s*stack|Stack\\s*technologiczny|Technologie)\\s*$)")) {
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
            default: return null;
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

    private String normalizeTag(String s) {
        if (s == null) return null;

        String t = s.replace('\u00A0', ' ')
                .trim()
                .replaceAll("\\s{2,}", " ");
        if (t.isEmpty()) return t;

        if (t.matches("(?i)^js$")) t = "JavaScript";
        else if (t.matches("(?i)^ts$")) t = "TypeScript";
        else if (t.matches("(?i)^node\\s*\\.\\s*js$|^nodejs$|^node\\.js$")) t = "Node.js";
        else if (t.matches("(?i)^dotnet$|^\\.net$|^net$")) t = ".NET";
        else if (t.matches("(?i)^c\\s*sharp$|^csharp$")) t = "C#";
        else if (t.matches("(?i)^pl\\s*/\\s*sql$")) t = "PL/SQL";
        else if (t.matches("(?i)^k8s$")) t = "Kubernetes";
        else if (t.matches("(?i)^gcp$")) t = "GCP";

        if (t.length() <= 3) {
            t = t.toUpperCase(Locale.ROOT);
        } else if (t.equals(t.toLowerCase(Locale.ROOT))) {
            t = Character.toUpperCase(t.charAt(0)) + t.substring(1);
        }

        return t;
    }

    private boolean isPlausibleTag(String t) {
        if (t == null || t.isBlank()) return false;

        String lc = t.toLowerCase(Locale.ROOT);

        if (LANGUAGE_WORDS.contains(lc)) return false;

        for (String bad : TAG_BLACKLIST) {
            if (lc.contains(bad)) return false;
        }

        if (t.length() > 30) return false;
        if (t.matches(".*\\d{3,}.*")) return false;
        if (t.matches("(?i).*(pln|eur|usd|brutto|netto|gross|net).*")) return false;

        return t.matches(".*[\\p{L}\\p{Nd}#.+].*");
    }

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

    public boolean isExpiredPage(String url, String html) {
        Document doc = Jsoup.parse(html, url);
        if (doc.selectFirst("*:matchesOwn(^\\s*Offer expired\\s*$)") != null) return true;
        if (doc.selectFirst("*:matchesOwn(^\\s*Oferta wygasła\\s*$)") != null) return true;
        if (doc.selectFirst("[data-test='offer-expired-banner'],[data-testid='offer-expired-banner']") != null) return true;
        String all = doc.text().toLowerCase(Locale.ROOT);
        return all.contains("offer expired") || all.contains("oferta wygasła");
    }

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
