package com.milosz.podsiadly.backend.ingest.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.milosz.podsiadly.backend.job.domain.JobLevel;
import com.milosz.podsiadly.backend.job.domain.SalaryPeriod;
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
            Pattern.compile("(\\d[\\d\\s\\u00A0]{0,20})\\s*(?:-|–|—)\\s*(\\d[\\d\\s\\u00A0]{0,20})\\s*(PLN|EUR|USD)?",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern SAL_SINGLE =
            Pattern.compile("\\b(\\d[\\d\\s\\u00A0]{0,20})\\s*(PLN|EUR|USD)\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern CURR_FALLBACK =
            Pattern.compile("\\b(PLN|EUR|USD)\\b", Pattern.CASE_INSENSITIVE);

    private static final int RX =
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

    private static final String LB = "(?<![\\p{L}\\p{N}])";
    private static final String RB = "(?![\\p{L}\\p{N}])";

    private static final Pattern RX_LEAD   = Pattern.compile(LB + "(?:lead|tech\\s*lead|team\\s*lead|principal|staff|head|manager|director|vp|vice\\s*president|c[-\\s]*level|chief|cto|cio|cfo|cmo|cpo|ceo|coo)" + RB, RX);
    private static final Pattern RX_SENIOR = Pattern.compile(LB + "(?:senior|starszy|sr\\.?|sen\\.?)" + RB, RX);
    private static final Pattern RX_MID    = Pattern.compile(LB + "(?:regular|mid(?!dle)|middle|średni\\w*)" + RB, RX);
    private static final Pattern RX_JUNIOR = Pattern.compile(LB + "(?:junior|młodszy|mlodszy|jr\\.?)" + RB, RX);
    private static final Pattern RX_INTERN = Pattern.compile(LB + "(?:intern(?:ship)?|trainee|apprentice|praktyk\\w*|staż\\w*|staz\\w*)" + RB, RX);

    private static final Pattern RX_B2B = Pattern.compile(LB + "B2B" + RB, RX);
    private static final Pattern RX_UOP = Pattern.compile(LB + "(?:UOP|u\\.?o\\.?p\\.?|umowa\\s*o\\s*prac[ęe]|permanent|employment\\s*contract|contract\\s*of\\s*employment|etat)" + RB, RX);
    private static final Pattern RX_UZ  = Pattern.compile(LB + "(?:UZ|zlecenie|umowa\\s*zlecenie|mandate)" + RB, RX);
    private static final Pattern RX_UOD = Pattern.compile(LB + "(?:UOD|dzie[łl]o|umowa\\s*o\\s*dzie[łl]o|specific\\s*task|civil\\s*contract)" + RB, RX);

    private static final Pattern RX_SAL_SUBTITLE = Pattern.compile(
            "(?i)\\b(?:net|gross|netto|brutto)?\\s*per\\s*(hour|day|week|month|year)\\b" +
                    "(?:\\s*[-–—]\\s*(b2b|permanent|uop|employment|mandate|zlecen|dzie|any))?\\b"
    );

    private static final Pattern RX_BAD_RANGE_LIKE =
            Pattern.compile("(?i)\\b\\d{2,6}\\s*(?:/|\\.)\\s*\\d{1,4}\\b|\\b\\d{5}\\s*[-–—]\\s*\\d{3,4}\\b");

    private final ObjectMapper om = new ObjectMapper();

    public ParsedOffer parse(String url, String html) {

        Document doc = Jsoup.parse(html, url);
        JsonNode ld = firstJobPostingJsonLd(doc);

        String title       = text(ld, "title");
        String description = text(ld, "description");
        String company     = text(ld.path("hiringOrganization"), "name");
        String city        = sanitizeLocation(firstCityFromLd(ld));
        Boolean remote     = parseRemoteFromLd(ld);
        Instant postedAt   = parseIsoInstant(text(ld, "datePosted"));

        if (blank(title)) {
            Element h1 = doc.selectFirst("h1");
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
                String candidate = sanitizeLocation(a.text());
                if (!blank(candidate) && candidate.length() <= 40) {
                    String lc = candidate.toLowerCase(Locale.ROOT);
                    if (lc.contains("all offers") || lc.contains("all locations")) continue;
                    city = candidate;
                    break;
                }
            }
        }

        if (remote == null) {
            String all = doc.text().toLowerCase(Locale.ROOT);
            if (all.contains("remote")) remote = true;
            else if (all.contains("onsite") || all.contains("office")) remote = false;
        }

        JobLevel level = firstNonNull(
                levelFromNext(html),
                levelFromChips(doc),
                levelFromTextStrict(nz(title, "") + " " + nz(description, "") + " " + doc.text())
        );

        LinkedHashSet<String> contracts = contractsFromNext(html);
        if (contracts.isEmpty()) {
            contracts.addAll(contractsFromHeroChips(doc));
            if (contracts.isEmpty()) contracts.addAll(contractsFromSalaryWidget(doc));
            if (contracts.isEmpty()) contracts.addAll(contractsFromTextStrict(doc.text()));
        }
        String contract = pickPreferredContract(contracts);

        SalarySelection salarySel = pickSalary(doc, html, ld, contract, contracts);

        Integer min = salarySel != null ? salarySel.min : null;
        Integer max = salarySel != null ? salarySel.max : null;
        String currency = salarySel != null ? salarySel.currency : null;
        SalaryPeriod salaryPeriod = salarySel != null ? salarySel.period : null;

        if (min == null && max == null) {
            currency = null;
            salaryPeriod = null;
        }

        List<ParsedSkill> techStack = extractTechStack(ld, doc, html);
        List<String> techTags = techStack.stream()
                .map(ParsedSkill::name)
                .filter(Objects::nonNull)
                .distinct()
                .limit(24)
                .toList();

        if (postedAt == null) postedAt = Instant.now();

        return new ParsedOffer(
                title,
                company,
                city,
                remote != null ? remote : Boolean.FALSE,
                level,
                min,
                max,
                currency,
                salaryPeriod,
                contract,
                techTags,
                techStack,
                postedAt,
                url,
                "JUSTJOIN",
                lastPath(url),
                description,
                contracts
        );
    }

    private static final class SalarySelection {
        final Integer min, max;
        final String currency;
        final String contractType;
        final SalaryPeriod period;
        final SalaryKind kind;
        final SalarySource source;

        SalarySelection(Integer min, Integer max, String currency, String contractType, SalaryPeriod period, SalaryKind kind, SalarySource source) {
            this.min = min;
            this.max = max;
            this.currency = currency;
            this.contractType = contractType;
            this.period = period;
            this.kind = kind != null ? kind : SalaryKind.UNKNOWN;
            this.source = source != null ? source : SalarySource.UNKNOWN;
        }
    }

    private enum SalaryKind { NET, GROSS, UNKNOWN }
    private enum SalarySource { DOM, NEXT, LD, UNKNOWN }

    private SalarySelection pickSalary(Document doc, String html, JsonNode ld, String chosenContract, Set<String> allContracts) {
        List<SalarySelection> dom = salariesFromSalaryWidget(doc);
        List<SalarySelection> next = salariesFromNextEmploymentTypesAll(html);
        List<SalarySelection> candidates = new ArrayList<>();
        candidates.addAll(dom);
        candidates.addAll(next);

        if (candidates.isEmpty()) {
            Salary ldSal = salaryFromLd(ld);
            if (ldSal != null && (ldSal.min != null || ldSal.max != null)) {
                Integer mn = ldSal.min != null ? ldSal.min : ldSal.max;
                Integer mx = ldSal.max != null ? ldSal.max : ldSal.min;
                String cur = blank(ldSal.currency) ? "PLN" : ldSal.currency;
                return new SalarySelection(mn, mx, cur, null, null, SalaryKind.UNKNOWN, SalarySource.LD);
            }
            return null;
        }

        String target = chosenContract;
        if (blank(target) && allContracts != null && !allContracts.isEmpty()) {
            target = pickPreferredContract(allContracts);
        }

        List<SalarySelection> pool = new ArrayList<>();
        if (!blank(target)) {
            for (SalarySelection s : candidates) {
                if (s.contractType != null && s.contractType.equalsIgnoreCase(target)) {
                    pool.add(s);
                }
            }
        }
        if (pool.isEmpty()) pool = candidates;

        return pickBestSalary(pool, target);
    }

    private SalarySelection pickBestSalary(List<SalarySelection> pool, String targetContract) {
        if (pool == null || pool.isEmpty()) return null;

        boolean wantB2B = "B2B".equalsIgnoreCase(targetContract);

        SalarySelection best = null;
        int bestScore = Integer.MIN_VALUE;

        for (SalarySelection s : pool) {
            if (s == null) continue;
            if (s.min == null && s.max == null) continue;

            Integer mn = s.min != null ? s.min : s.max;
            Integer mx = s.max != null ? s.max : s.min;

            if (mn != null && mn > 2_000_000) continue;
            if (mx != null && mx > 2_000_000) continue;

            int score = 0;

            if (s.source == SalarySource.DOM) score += 2000;
            else if (s.source == SalarySource.NEXT) score += 500;
            else if (s.source == SalarySource.LD) score += 100;

            if (!blank(targetContract) && s.contractType != null && s.contractType.equalsIgnoreCase(targetContract)) score += 1000;

            if (wantB2B) {
                if (s.kind == SalaryKind.NET) score += 200;
                else if (s.kind == SalaryKind.GROSS) score += 50;
            } else {
                if (s.kind == SalaryKind.GROSS) score += 200;
                else if (s.kind == SalaryKind.NET) score += 50;
            }

            if (s.period != null) score += 150;
            else score -= 200;

            if (s.min != null && s.max != null) score += 25;

            if (mx != null) score += Math.min(500, mx / 100);

            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }

        return best;
    }

    private List<SalarySelection> salariesFromSalaryWidget(Document doc) {
        if (doc == null) return List.of();

        List<Element> salaryCards = findSalaryCardRoots(doc);
        if (salaryCards.isEmpty()) return List.of();

        List<SalarySelection> out = new ArrayList<>();
        for (Element card : salaryCards) {
            out.addAll(extractSalaryEntriesFromSalaryCard(card));
        }

        LinkedHashMap<String, SalarySelection> uniq = new LinkedHashMap<>();
        for (SalarySelection s : out) {
            String key = (s.min + "|" + s.max + "|" + s.currency + "|" + s.period + "|" + s.contractType + "|" + s.kind + "|" + s.source);
            uniq.putIfAbsent(key, s);
        }
        return new ArrayList<>(uniq.values());
    }

    private List<Element> findSalaryCardRoots(Document doc) {
        Elements salaryHeads = doc.select("*:matchesOwn((?i)^\\s*salary\\s*$)");
        List<Element> roots = new ArrayList<>();

        for (Element head : salaryHeads) {
            Element cur = head;
            for (int up = 0; up < 10 && cur != null; up++) {
                Element parent = cur.parent();
                if (parent == null) break;

                String text = parent.text();
                boolean hasSubtitle = RX_SAL_SUBTITLE.matcher(text).find();
                boolean hasCurrency = CURR_FALLBACK.matcher(text).find();
                boolean hasSomeAmount = SAL_RANGE.matcher(text).find() || SAL_SINGLE.matcher(text).find();

                if (hasSubtitle && hasSomeAmount && hasCurrency) {
                    roots.add(parent);
                    break;
                }
                cur = parent;
            }
        }

        LinkedHashSet<Element> uniq = new LinkedHashSet<>(roots);
        return new ArrayList<>(uniq);
    }

    private List<SalarySelection> extractSalaryEntriesFromSalaryCard(Element card) {
        if (card == null) return List.of();

        List<SalarySelection> out = new ArrayList<>();
        List<Element> subtitleEls = new ArrayList<>();
        for (Element e : card.getAllElements()) {
            String t = clean(e.ownText());
            if (blank(t)) continue;
            if (looksLikeSalarySubtitle(t)) subtitleEls.add(e);
        }

        for (Element subEl : subtitleEls) {
            String subtitle = clean(subEl.text());
            SalaryKind kind = parseKindLoose(subtitle);
            SalaryPeriod period = parsePeriodFromSubtitle(subtitle);
            String contract = normalizeContractFromSubtitle(subtitle);

            if (period == null) continue;

            SalaryTriplet amt = findAmountNearSubtitle(subEl);
            if (amt == null) continue;

            String rawAmountText = clean(amt.rawText);
            if (!blank(rawAmountText) && RX_BAD_RANGE_LIKE.matcher(rawAmountText).find()) continue;

            Integer mn = amt.min;
            Integer mx = amt.max;

            if (mn == null && mx == null) continue;

            String cur = blank(amt.currency) ? "PLN" : amt.currency;

            out.add(new SalarySelection(mn, mx, cur, contract, period, kind, SalarySource.DOM));
        }

        return out;
    }

    private boolean looksLikeSalarySubtitle(String t) {
        if (blank(t)) return false;
        return RX_SAL_SUBTITLE.matcher(t).find();
    }

    private SalaryTriplet findAmountNearSubtitle(Element subtitleEl) {
        if (subtitleEl == null) return null;

        Element cur = subtitleEl;
        for (int steps = 0; steps < 6 && cur != null; steps++) {
            Element prev = cur.previousElementSibling();
            while (prev != null) {
                String candidate = clean(prev.text());
                SalaryTriplet t = parseAmountStrict(candidate);
                if (t != null) return t;
                prev = prev.previousElementSibling();
            }
            cur = cur.parent();
        }

        Element p = subtitleEl.parent();
        if (p != null) {
            Elements children = p.children();
            for (int i = 0; i < children.size(); i++) {
                String candidate = clean(children.get(i).text());
                SalaryTriplet t = parseAmountStrict(candidate);
                if (t != null) return t;
            }
        }

        return null;
    }

    private SalaryTriplet parseAmountStrict(String txt) {
        if (blank(txt)) return null;

        if (!CURR_FALLBACK.matcher(txt).find()) return null;

        Matcher rm = SAL_RANGE.matcher(txt);
        if (rm.find()) {
            Integer a = parseIntStripSafe(rm.group(1));
            Integer b = parseIntStripSafe(rm.group(2));
            String cur = rm.group(3) != null ? rm.group(3).toUpperCase(Locale.ROOT) : null;

            if (blank(cur)) {
                Matcher cm = CURR_FALLBACK.matcher(txt);
                if (cm.find()) cur = cm.group(1).toUpperCase(Locale.ROOT);
            }
            if (blank(cur)) cur = "PLN";
            if (a == null || b == null) return null;

            return new SalaryTriplet(a, b, cur, txt);
        }

        Matcher sm = SAL_SINGLE.matcher(txt);
        if (sm.find()) {
            Integer v = parseIntStripSafe(sm.group(1));
            String cur = sm.group(2) != null ? sm.group(2).toUpperCase(Locale.ROOT) : "PLN";
            if (v == null) return null;
            return new SalaryTriplet(v, v, cur, txt);
        }

        return null;
    }

    private static SalaryKind parseKindLoose(String raw) {
        if (raw == null) return SalaryKind.UNKNOWN;
        String t = raw.toLowerCase(Locale.ROOT);
        if (t.contains("net") || t.contains("netto")) return SalaryKind.NET;
        if (t.contains("gross") || t.contains("brutto")) return SalaryKind.GROSS;
        return SalaryKind.UNKNOWN;
    }

    private static SalaryPeriod parsePeriodFromSubtitle(String sub) {
        if (sub == null) return null;
        String t = sub.toLowerCase(Locale.ROOT);

        if (t.contains("per hour") || t.contains("/hour") || t.contains(" /h") || t.contains("/h")) return SalaryPeriod.HOUR;
        if (t.contains("per day")  || t.contains("/day")  || t.contains(" dzien") || t.contains(" dzień") || t.contains("dzien")) return SalaryPeriod.DAY;
        if (t.contains("per week") || t.contains("/week") || t.contains("tyg")) return SalaryPeriod.WEEK;
        if (t.contains("per month")|| t.contains("/month")|| t.contains("mies")) return SalaryPeriod.MONTH;
        if (t.contains("per year") || t.contains("/year") || t.contains("annual") || t.contains("rok")) return SalaryPeriod.YEAR;

        return null;
    }

    private static String normalizeContractFromSubtitle(String sub) {
        if (sub == null) return null;
        String t = sub.toLowerCase(Locale.ROOT);

        if (t.contains("any")) return null;
        if (t.contains("b2b")) return "B2B";
        if (t.contains("permanent") || t.contains("uop") || t.contains("umowa o prac") || t.contains("employment")) return "UOP";
        if (t.contains("mandate") || t.contains("zlecen")) return "UZ";
        if (t.contains("specific task") || t.contains("dzie")) return "UOD";
        return null;
    }

    private List<SalarySelection> salariesFromNextEmploymentTypesAll(String html) {
        String arr = extractNextFieldArray(html, "\"employmentTypes\"");
        if (arr == null) return List.of();

        List<SalarySelection> out = new ArrayList<>();
        try {
            ArrayNode node = (ArrayNode) om.readTree(arr);
            for (JsonNode it : node) {
                String type = text(it, "type");
                Integer from = intOrNull(it, "from");
                Integer to = intOrNull(it, "to");
                String curr = optUpper(text(it, "currency"));
                String unit = text(it, "unit");
                String salaryType = text(it, "salaryType");

                if (from == null && to == null) continue;

                Integer min = from != null ? from : to;
                Integer max = to != null ? to : from;

                SalaryPeriod period = parseUnit(unit);
                if (blank(curr)) curr = "PLN";

                SalaryKind kind = parseKindLoose(salaryType);
                String mappedContract = normalizeContractToken(type);

                out.add(new SalarySelection(min, max, curr, mappedContract, period, kind, SalarySource.NEXT));
            }
        } catch (Exception ignore) {
            return List.of();
        }
        return out;
    }

    private static final class Salary {
        final Integer min, max;
        final String currency;
        Salary(Integer min, Integer max, String currency) { this.min = min; this.max = max; this.currency = currency; }
    }

    private static Salary salaryFromLd(JsonNode ld) {
        if (ld == null || ld.isMissingNode()) return null;
        JsonNode bs = ld.get("baseSalary");
        if (bs == null || bs.isNull()) return null;

        Integer min = null, max = null;
        String curr = null;

        if (bs.isArray()) {
            for (JsonNode it : bs) {
                JsonNode val = it.get("value");
                if (val != null) {
                    if (val.has("minValue") || val.has("maxValue")) {
                        if (min == null) min = intOrNull(val, "minValue");
                        if (max == null) max = intOrNull(val, "maxValue");
                    } else if (val.canConvertToInt()) {
                        Integer v = val.asInt();
                        if (min == null) min = v;
                        if (max == null) max = v;
                    }
                }
                if (curr == null) curr = optUpper(text(it, "currency"));
            }
        } else {
            JsonNode val = bs.get("value");
            if (val != null) {
                if (val.has("minValue") || val.has("maxValue")) {
                    min = intOrNull(val, "minValue");
                    max = intOrNull(val, "maxValue");
                } else if (val.canConvertToInt()) {
                    min = max = val.asInt();
                }
            }
            curr = optUpper(text(bs, "currency"));
        }

        if (min == null && max == null && curr == null) return null;
        return new Salary(min, max, curr);
    }

    private static SalaryPeriod parseUnit(String unit) {
        if (unit == null || unit.isBlank()) return null;
        String u = unit.trim().toLowerCase(Locale.ROOT);

        return switch (u) {
            case "hour", "hourly" -> SalaryPeriod.HOUR;
            case "day", "daily" -> SalaryPeriod.DAY;
            case "week", "weekly" -> SalaryPeriod.WEEK;
            case "month", "monthly" -> SalaryPeriod.MONTH;
            case "year", "annual", "yearly" -> SalaryPeriod.YEAR;
            default -> null;
        };
    }

    private static final class SalaryTriplet {
        final Integer min, max;
        final String currency;
        final String rawText;

        SalaryTriplet(Integer min, Integer max, String currency, String rawText) {
            this.min = min;
            this.max = max;
            this.currency = currency;
            this.rawText = rawText;
        }
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
        JobLevel best = null;
        for (Element e : doc.getAllElements()) {
            String t = e.ownText();
            if (t == null || t.isBlank()) continue;
            JobLevel lv = mapLevelString(t);
            best = pickHigher(best, lv);
        }
        return best;
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

    private JobLevel mapLevelString(String s) {
        if (blank(s)) return null;
        String t = s.trim().toLowerCase(Locale.ROOT);

        if (t.matches("(?i)intern(ship)?|trainee|apprentice|praktyk\\p{L}*|staż\\p{L}*|staz\\p{L}*"))
            return JobLevel.INTERNSHIP;

        if (t.matches("(?i)jr\\.?|junior|młodsz\\p{L}*|mlodsz\\p{L}*"))
            return JobLevel.JUNIOR;

        if (t.matches("(?i)mid(dle)?|regular|średni\\p{L}*"))
            return JobLevel.MID;

        if (t.matches("(?i)sr\\.?|senior|starsz\\p{L}*"))
            return JobLevel.SENIOR;

        if (t.matches("(?i)lead|principal|staff|head|manager|director|vp|vice\\s*president|c[-\\s]*level|chief|cto|cio|cfo|cmo|cpo|ceo|coo"))
            return JobLevel.LEAD;

        return null;
    }

    private JobLevel pickHigher(JobLevel a, JobLevel b) {
        if (a == null) return b;
        if (b == null) return a;
        List<JobLevel> order = List.of(JobLevel.INTERNSHIP, JobLevel.JUNIOR, JobLevel.MID, JobLevel.SENIOR, JobLevel.LEAD);
        return order.indexOf(b) > order.indexOf(a) ? b : a;
    }

    private static String normalizeContractToken(String raw) {
        if (raw == null) return null;
        String t = raw.trim().toLowerCase(Locale.ROOT);
        if (t.matches("(?i)^any$")) return null; // ✅ Any off
        if (t.matches("(?i)^b2b$")) return "B2B";
        if (t.matches("(?i)^(uop|umowa\\s*o\\s*prac[ęe]|permanent|employment\\s*contract|contract\\s*of\\s*employment|etat)$")) return "UOP";
        if (t.matches("(?i)^(uz|umowa\\s*zlecenie|mandate)$")) return "UZ";
        if (t.matches("(?i)^(uod|umowa\\s*o\\s*dzie[łl]o|specific\\s*task|civil\\s*contract)$")) return "UOD";

        return null;
    }

    private LinkedHashSet<String> contractsFromNext(String html) {
        String arr = extractNextFieldArray(html, "\"employmentTypes\"");
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (arr == null) return out;

        try {
            ArrayNode node = (ArrayNode) om.readTree(arr);
            for (JsonNode it : node) {
                String mapped = normalizeContractToken(text(it, "type"));
                if (mapped != null) out.add(mapped);
            }
        } catch (Exception ignore) {}

        return out;
    }

    private LinkedHashSet<String> contractsFromHeroChips(Document doc) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Element h1 = doc.selectFirst("h1");
        if (h1 == null) return out;

        Element scope = h1.parent();
        for (int i = 0; i < 6 && scope != null && out.isEmpty(); i++, scope = scope.parent()) {
            String txt = scope.text();
            if (RX_B2B.matcher(txt).find()) out.add("B2B");
            if (RX_UOP.matcher(txt).find()) out.add("UOP");
            if (RX_UZ.matcher(txt).find())  out.add("UZ");
            if (RX_UOD.matcher(txt).find()) out.add("UOD");
        }
        return out;
    }

    private LinkedHashSet<String> contractsFromSalaryWidget(Document doc) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Element head : doc.select("*:matchesOwn((?i)^\\s*salary\\s*$)")) {
            Element scope = head.parent();
            for (int i = 0; i < 10 && scope != null; i++, scope = scope.parent()) {
                String txt = scope.text();
                if (RX_B2B.matcher(txt).find()) out.add("B2B");
                if (RX_UOP.matcher(txt).find()) out.add("UOP");
                if (RX_UZ.matcher(txt).find())  out.add("UZ");
                if (RX_UOD.matcher(txt).find()) out.add("UOD");
                if (!out.isEmpty()) break;
            }
        }
        return out;
    }

    private LinkedHashSet<String> contractsFromTextStrict(String txt) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (txt == null || txt.isBlank()) return out;
        if (RX_B2B.matcher(txt).find()) out.add("B2B");
        if (RX_UOP.matcher(txt).find()) out.add("UOP");
        if (RX_UZ.matcher(txt).find())  out.add("UZ");
        if (RX_UOD.matcher(txt).find()) out.add("UOD");
        return out;
    }

    private String pickPreferredContract(Set<String> set) {
        if (set == null || set.isEmpty()) return null;
        for (String pref : List.of("B2B","UOP","UZ","UOD")) {
            if (set.contains(pref)) return pref;
        }
        for (String v : set) {
            if (v == null) continue;
            if ("ANY".equalsIgnoreCase(v)) continue;
            return v;
        }
        return null;
    }

    public record ParsedSkill(String name, String levelLabel, Integer levelValue, String source) {}

    private List<ParsedSkill> extractTechStack(JsonNode ld, Document doc, String html) {
        List<ParsedSkill> dom = techStackFromDom(doc);
        if (!dom.isEmpty()) return dom;

        List<ParsedSkill> next = techStackFromNext(html);
        if (!next.isEmpty()) return next;

        return List.of();
    }

    private List<ParsedSkill> techStackFromDom(Document doc) {
        if (doc == null) return List.of();

        Elements heads = doc.select("*:matchesOwn((?i)^\\s*tech\\s*stack\\s*$)");
        if (heads.isEmpty()) return List.of();

        LinkedHashMap<String, ParsedSkill> out = new LinkedHashMap<>();

        for (Element head : heads) {
            Element ul = findNearestUlAfter(head);
            if (ul == null) {
                ul = findUlInClosestSection(head);
            }
            if (ul == null) continue;

            for (Element li : ul.select("li")) {
                ParsedSkill ps = parseTechChip(li);
                if (ps != null && !blank(ps.name())) out.putIfAbsent(ps.name().toLowerCase(Locale.ROOT), ps);
            }

            if (out.isEmpty()) {
                for (Element chip : ul.select("div")) {
                    ParsedSkill ps = parseTechChip(chip);
                    if (ps != null && !blank(ps.name())) out.putIfAbsent(ps.name().toLowerCase(Locale.ROOT), ps);
                }
            }
        }

        return new ArrayList<>(out.values());
    }

    private Element findNearestUlAfter(Element head) {
        Element cur = head;
        for (int up = 0; up < 6 && cur != null; up++, cur = cur.parent()) {
            Element sib = cur.nextElementSibling();
            int guard = 0;
            while (sib != null && guard++ < 12) {
                if ("ul".equalsIgnoreCase(sib.tagName())) return sib;
                Element inner = sib.selectFirst("ul");
                if (inner != null) return inner;
                sib = sib.nextElementSibling();
            }

            Element parent = cur.parent();
            if (parent != null) {
                Element inline = parent.selectFirst("ul");
                if (inline != null) return inline;
            }
        }
        return null;
    }

    private Element findUlInClosestSection(Element head) {
        Element cur = head;
        for (int up = 0; up < 10 && cur != null; up++, cur = cur.parent()) {
            Element ul = cur.selectFirst("ul");
            if (ul != null) return ul;
        }
        return null;
    }

    private ParsedSkill parseTechChip(Element chipRoot) {
        if (chipRoot == null) return null;

        List<String> parts = new ArrayList<>();
        for (Element e : chipRoot.getAllElements()) {
            String t = clean(e.ownText());
            if (blank(t)) continue;
            if (t.length() > 60) continue;
            parts.add(t);
        }

        LinkedHashSet<String> uniq = new LinkedHashSet<>(parts);
        parts = new ArrayList<>(uniq);

        if (parts.isEmpty()) {
            String t = clean(chipRoot.text());
            if (blank(t) || t.length() > 80) return null;
            parts = List.of(t);
        }

        String levelLabel = null;
        Integer levelValue = null;

        for (String p : parts) {
            Integer lv = mapSkillLevelValue(p);
            if (lv != null) {
                levelLabel = p;
                levelValue = lv;
                break;
            }
        }

        String name = null;
        for (String p : parts) {
            String pp = p.trim();
            if (blank(pp)) continue;
            if (levelLabel != null && pp.equalsIgnoreCase(levelLabel)) continue;
            if (pp.matches("^\\d+$")) continue;
            if (pp.equalsIgnoreCase("required") || pp.equalsIgnoreCase("nice to have")) continue;

            name = pp;
            break;
        }

        if (blank(name)) return null;

        return new ParsedSkill(name, levelLabel, levelValue, "DOM_TECH_STACK");
    }

    private List<ParsedSkill> techStackFromNext(String html) {
        if (blank(html)) return List.of();
        List<String> possibleArrays = List.of(
                "\"techStack\"",
                "\"techstack\"",
                "\"skills\"",
                "\"requiredSkills\"",
                "\"stack\""
        );

        LinkedHashMap<String, ParsedSkill> out = new LinkedHashMap<>();

        for (String key : possibleArrays) {
            String arr = extractNextFieldArray(html, key);
            if (arr == null) continue;

            try {
                JsonNode n = om.readTree(arr);
                if (!n.isArray()) continue;
                for (JsonNode it : n) {
                    if (it == null || it.isNull()) continue;
                    if (it.isTextual()) {
                        String name = clean(it.asText());
                        if (!blank(name) && name.length() <= 60) {
                            out.putIfAbsent(name.toLowerCase(Locale.ROOT),
                                    new ParsedSkill(name, null, null, "NEXT_FALLBACK"));
                        }
                    } else if (it.has("name")) {
                        String name = clean(it.get("name").asText(null));
                        if (!blank(name) && name.length() <= 60) {
                            String lvl = it.has("level") ? clean(it.get("level").asText(null)) : null;
                            Integer lv = mapSkillLevelValue(lvl);
                            out.putIfAbsent(name.toLowerCase(Locale.ROOT),
                                    new ParsedSkill(name, lvl, lv, "NEXT_FALLBACK"));
                        }
                    }
                }
            } catch (Exception ignore) {
            }

            if (!out.isEmpty()) break;
        }

        return new ArrayList<>(out.values());
    }

    private Integer mapSkillLevelValue(String levelLabel) {
        if (blank(levelLabel)) return null;
        String t = levelLabel.trim().toLowerCase(Locale.ROOT);
        if (t.equals("beginner") || t.equals("junior") || t.equals("basic") || t.equals("podstawowy")) return 1;
        if (t.equals("regular") || t.equals("intermediate") || t.equals("średni") || t.equals("sredni")) return 2;
        if (t.equals("advanced") || t.equals("zaawansowany")) return 3;
        if (t.equals("master") || t.equals("expert") || t.equals("ekspert")) return 4;

        return null;
    }

    private String findNextStringAny(String html, String... keys) {
        for (String key : keys) {
            Pattern p = Pattern.compile(key + "\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
            Matcher m = p.matcher(html);
            if (m.find()) return unescapeJson(m.group(1)).trim();
        }
        return null;
    }

    private String extractNextFieldArray(String html, String fieldNameQuoted) {
        int keyIdx = html.indexOf(fieldNameQuoted);
        if (keyIdx < 0) return null;

        int bracketStart = html.indexOf('[', keyIdx);
        if (bracketStart < 0) return null;

        int i = bracketStart;
        int depth = 0;
        boolean inStr = false;
        char strChar = 0;

        for (; i < html.length(); i++) {
            char c = html.charAt(i);
            if (inStr) {
                if (c == '\\') { i++; continue; }
                if (c == strChar) inStr = false;
            } else {
                if (c == '"' || c == '\'') { inStr = true; strChar = c; }
                else if (c == '[') depth++;
                else if (c == ']') {
                    depth--;
                    if (depth == 0) { i++; break; }
                }
            }
        }
        if (depth != 0) return null;

        String json = html.substring(bracketStart, i);

        try {
            om.readTree(json);
            return json;
        } catch (Exception e) {
            try {
                String repaired = unescapeJson(json);
                om.readTree(repaired);
                return repaired;
            } catch (Exception ignore) {
                return null;
            }
        }
    }

    private static String unescapeJson(String s) {
        String x = s.replace("\\\"", "\"");
        return x.replace("\\\\", "\\");
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

    private static Boolean parseRemoteFromLd(JsonNode ld) {
        String jlt = text(ld, "jobLocationType");
        if (!blank(jlt)) return jlt.toUpperCase(Locale.ROOT).contains("TELECOMMUTE");
        String city = firstCityFromLd(ld);
        if (!blank(city) && "remote".equalsIgnoreCase(city)) return true;
        return null;
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
            SalaryPeriod salaryPeriod,
            String contract,
            List<String> techTags,
            List<ParsedSkill> techStack,
            Instant publishedAt,
            String url,
            String source,
            String externalId,
            String description,
            Set<String> contracts
    ) {}

    private static String text(JsonNode n, String field) {
        return n != null && n.has(field) && !n.get(field).isNull() ? n.get(field).asText(null) : null;
    }

    private static Integer intOrNull(JsonNode n, String field) {
        return (n != null && n.has(field) && n.get(field).isNumber()) ? n.get(field).asInt() : null;
    }

    private static String optUpper(String s) { return blank(s) ? null : s.toUpperCase(Locale.ROOT); }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private static String nz(String s, String def) { return blank(s) ? def : s; }

    private static Instant parseIsoInstant(String s) {
        if (blank(s)) return null;
        try { return OffsetDateTime.parse(s).toInstant(); }
        catch (DateTimeParseException e) { return null; }
    }

    private static Integer parseIntStripSafe(String s) {
        if (s == null) return null;
        String x = s.replace('\u00A0', ' ').replaceAll("\\s+", "");
        try {
            return Integer.parseInt(x);
        } catch (Exception e) {
            return null;
        }
    }

    private static String lastPath(String url) {
        int q = url.indexOf('?');
        if (q >= 0) url = url.substring(0, q);
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        int i = url.lastIndexOf('/');
        return i >= 0 ? url.substring(i + 1) : url;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... vals) {
        for (T v : vals) if (v != null) return v;
        return null;
    }

    private static String sanitizeLocation(String s) {
        if (s == null) return null;
        String t = s.replace('\u00A0', ' ').trim().replaceAll("\\s{2,}", " ");
        t = t.replaceAll("^[\\s,.-]+", "").trim();
        return t.isBlank() ? null : t;
    }

    private static String clean(String s) {
        if (s == null) return null;
        return s.replace('\u00A0', ' ').replaceAll("\\s{2,}", " ").trim();
    }
}
