package com.milosz.podsiadly.careerhub.agentcrawler.ingest.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.milosz.podsiadly.careerhub.agentcrawler.ingest.model.ExternalJobOfferData;
import com.milosz.podsiadly.careerhub.agentcrawler.ingest.model.ParsedExternalOffer;
import com.milosz.podsiadly.careerhub.agentcrawler.job.domain.ContractType;
import com.milosz.podsiadly.careerhub.agentcrawler.job.domain.JobLevel;
import com.milosz.podsiadly.careerhub.agentcrawler.job.domain.JobSource;
import com.milosz.podsiadly.careerhub.agentcrawler.job.domain.SalaryPeriod;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PracujParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern NEXT_DATA =
            Pattern.compile("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", Pattern.DOTALL);

    public ParsedExternalOffer parse(String detailsUrl, String html) throws Exception {
        JsonNode offer = extractJobOfferNode(html);
        JsonNode attr = offer.path("attributes");

        String externalId = firstNonBlank(
                offer.path("jobOfferWebId").asText(""),
                offer.path("offerId").asText(""),
                ""
        );

        String url = firstNonBlank(
                attr.path("offerAbsoluteUrl").asText(""),
                detailsUrl
        );

        String title = attr.path("jobTitle").asText("");

        String companyName = attr.path("displayEmployerName").asText("");

        String cityName = firstNonBlank(
                attr.at("/workplaces/0/inlandLocation/location/name").asText(""),
                attr.at("/workplaces/0/displayAddress").asText(""),
                attr.path("city").asText(""),
                ""
        );

        boolean remote = isRemote(attr);

        JobLevel level = normalizeLevel(firstNonBlank(
                attr.at("/employment/positionLevels/0/name").asText(""),
                attr.at("/employment/positionLevels/0/code").asText(""),
                ""
        ));

        ContractInfo contractInfo = extractContractsAndSalary(attr);

        String applyUrl = firstNonBlank(
                attr.at("/applying/applyProxy/applyUrl").asText(""),
                attr.at("/applying/applyUrl").asText(""),
                url
        );

        Instant publishedAt = parseInstantSafe(
                offer.at("/publicationDetails/lastPublishedUtc").asText(""),
                offer.at("/publicationDetails/dateOfInitialPublicationUtc").asText("")
        );

        boolean active = offer.at("/publicationDetails/isActive").asBoolean(false);

        String description = buildDescriptionFromSections(offer);

        List<String> techTags = extractTechTags(offer);

        return new ParsedExternalOffer(
                JobSource.PRACUJ,
                externalId,
                new ExternalJobOfferData(
                        title,
                        description,
                        companyName,
                        cityName,
                        remote,
                        level,
                        contractInfo.mainContract,
                        contractInfo.contracts,
                        contractInfo.salaryMin,
                        contractInfo.salaryMax,
                        blankToNull(contractInfo.currency),
                        contractInfo.salaryPeriod,
                        url,
                        applyUrl,
                        techTags,
                        List.of(),
                        publishedAt,
                        active
                )
        );
    }

    private static JsonNode extractJobOfferNode(String html) throws Exception {
        Matcher m = NEXT_DATA.matcher(html);
        if (!m.find()) throw new IllegalStateException("No __NEXT_DATA__ found");

        JsonNode root = MAPPER.readTree(m.group(1));
        JsonNode queries = root.at("/props/pageProps/dehydratedState/queries");
        if (!queries.isArray()) throw new IllegalStateException("dehydratedState.queries is not an array");

        for (JsonNode q : queries) {
            JsonNode qk = q.path("queryKey");
            if (qk.isArray() && qk.size() > 0 && "jobOffer".equals(qk.get(0).asText())) {
                JsonNode offer = q.at("/state/data");
                if (offer.isMissingNode() || offer.isNull()) break;
                return offer;
            }
        }
        throw new IllegalStateException("jobOffer not found in __NEXT_DATA__");
    }

    private static boolean isRemote(JsonNode attr) {
        boolean entirelyRemote = attr.at("/employment/entirelyRemoteWork").asBoolean(false);

        String modes = attr.at("/employment/workModes").toString().toLowerCase(Locale.ROOT);

        boolean modeRemote = modes.contains("\"remote\"");

        return entirelyRemote || modeRemote;
    }

    private static String buildDescriptionFromSections(JsonNode offer) {
        Map<String, String> niceHeaders = Map.of(
                "about-project", "About the project",
                "responsibilities", "Your responsibilities",
                "requirements", "Requirements",
                "offered", "What we offer",
                "benefits", "Benefits",
                "additional-module", "Additional info",
                "about-us-extended", "About the company"
        );

        StringBuilder sb = new StringBuilder(2048);

        JsonNode sections = offer.path("sections");
        if (!sections.isArray()) return "";

        for (JsonNode sec : sections) {
            String sectionType = sec.path("sectionType").asText("");
            if (sectionType.isBlank()) continue;

            boolean relevant =
                    sectionType.equals("about-project")
                            || sectionType.equals("responsibilities")
                            || sectionType.equals("requirements")
                            || sectionType.equals("offered")
                            || sectionType.equals("benefits")
                            || sectionType.equals("additional-module")
                            || sectionType.equals("about-us-extended");

            if (!relevant) continue;

            String header = firstNonBlank(
                    sec.path("title").asText(""),
                    niceHeaders.getOrDefault(sectionType, sectionType)
            );

            String block = sectionToText(sec);
            if (block.isBlank()) continue;

            if (!sb.isEmpty()) sb.append("\n\n");
            sb.append(header.trim()).append("\n");
            sb.append(block.trim());
        }

        if (sb.isEmpty()) {
            String shortDesc = offer.path("attributes").path("description").asText("");
            return shortDesc == null ? "" : shortDesc.trim();
        }

        return sb.toString().trim();
    }

    private static String sectionToText(JsonNode sec) {
        StringBuilder out = new StringBuilder(1024);

        JsonNode model = sec.path("model");
        if (model.isMissingNode() || model.isNull()) {
            JsonNode sub = sec.path("subSections");
            if (sub.isArray()) {
                for (JsonNode s : sub) {
                    String t = sectionToText(s);
                    if (!t.isBlank()) {
                        if (!out.isEmpty()) out.append("\n");
                        out.append(t);
                    }
                }
            }
            return out.toString();
        }

        String modelType = model.path("modelType").asText("");

        if ("multi-paragraph".equals(modelType)) {
            for (JsonNode p : model.path("paragraphs")) {
                String t = p.asText("").trim();
                if (!t.isBlank()) out.append(t).append("\n");
            }
            return out.toString().trim();
        }

        if ("bullets".equals(modelType)) {
            for (JsonNode b : model.path("bullets")) {
                String t = b.asText("").trim();
                if (!t.isBlank()) out.append("- ").append(t).append("\n");
            }
            return out.toString().trim();
        }

        if ("open-dictionary".equals(modelType) || "open-dictionary-with-icons".equals(modelType)) {
            for (JsonNode it : model.path("items")) {
                String t = it.path("name").asText("").trim();
                if (!t.isBlank()) out.append("- ").append(t).append("\n");
            }
            for (JsonNode it : model.path("customItems")) {
                String t = it.path("name").asText("").trim();
                if (!t.isBlank()) out.append("- ").append(t).append("\n");
            }
            return out.toString().trim();
        }

        String raw = model.toString();
        return raw == null ? "" : raw.trim();
    }

    private static List<String> extractTechTags(JsonNode offer) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();

        JsonNode sections = offer.path("sections");
        if (!sections.isArray()) return List.of();

        for (JsonNode sec : sections) {
            if (!"technologies".equals(sec.path("sectionType").asText(""))) continue;

            JsonNode subSections = sec.path("subSections");
            if (!subSections.isArray()) continue;

            for (JsonNode sub : subSections) {
                String subType = sub.path("sectionType").asText("");

                if (!subType.contains("expected")) continue;

                JsonNode model = sub.path("model");
                if (model.isMissingNode() || model.isNull()) continue;

                for (JsonNode it : model.path("items")) {
                    String n = it.path("name").asText("").trim();
                    if (!n.isBlank()) tags.add(n);
                }
                for (JsonNode it : model.path("customItems")) {
                    String n = it.path("name").asText("").trim();
                    if (!n.isBlank()) tags.add(n);
                }
            }
        }

        return new ArrayList<>(tags);
    }

    // ------------------------------------------------------------
    // Contracts & salary
    // ------------------------------------------------------------

    /**
     * FIX: tu przyjmujemy attributes (attr), bo employment jest w attr.employment
     */
    private ContractInfo extractContractsAndSalary(JsonNode attr) {
        JsonNode arr = attr.at("/employment/typesOfContracts");
        LinkedHashSet<ContractType> contracts = new LinkedHashSet<>();

        ContractType mainContract = null;
        Integer min = null;
        Integer max = null;
        String currency = "";
        SalaryPeriod salaryPeriod = SalaryPeriod.MONTH;

        if (arr.isArray()) {
            for (JsonNode c : arr) {

                String ctRaw = firstNonBlank(
                        c.path("name").asText(""),
                        c.path("pracujPlName").asText(""),
                        c.path("code").asText(""),
                        ""
                );
                ContractType ct = normalizeContract(ctRaw);
                if (ct != null) {
                    contracts.add(ct);
                    if (mainContract == null) mainContract = ct;
                }

                JsonNode sal = c.path("salary");
                if (sal != null && !sal.isMissingNode() && !sal.isNull()) {

                    int from = sal.path("from").asInt(0);
                    int to = sal.path("to").asInt(0);

                    String cur = firstNonBlank(
                            sal.at("/currency/code").asText(""),
                            sal.path("currency").asText(""),
                            ""
                    );

                    String per = firstNonBlank(
                            sal.at("/timeUnit/longForm/name").asText(""),
                            sal.at("/timeUnit/shortForm/name").asText(""),
                            sal.at("/timeUnit/pracujPlName").asText(""),
                            ""
                    );

                    if ((from > 0 || to > 0) && (min == null && max == null)) {
                        min = (from > 0) ? from : null;
                        max = (to > 0) ? to : null;
                        currency = cur;
                        salaryPeriod = normalizeSalaryPeriod(per);
                    }
                }
            }
        }

        return new ContractInfo(min, max, currency, salaryPeriod, mainContract, contracts);
    }

    // ------------------------------------------------------------
    // Normalizers + small utils
    // ------------------------------------------------------------

    private static JobLevel normalizeLevel(String raw) {
        if (raw == null) return null;
        String r = raw.trim().toLowerCase(Locale.ROOT);
        if (r.isBlank()) return null;

        if (r.contains("intern") || r.contains("praktyk") || r.contains("trainee")) return JobLevel.INTERNSHIP;
        if (r.contains("jun")) return JobLevel.JUNIOR;
        if (r.contains("mid") || r.contains("regular")) return JobLevel.MID;
        if (r.contains("sen")) return JobLevel.SENIOR;
        if (r.contains("lead") || r.contains("manag")) return JobLevel.LEAD;

        return null;
    }

    private static ContractType normalizeContract(String raw) {
        if (raw == null) return null;
        String r = raw.trim().toLowerCase(Locale.ROOT);
        if (r.isBlank()) return null;

        if (r.contains("b2b")) return ContractType.B2B;
        if (r.contains("employment contract") || r.contains("umowa o prac") || r.contains("uop")) return ContractType.UOP;
        if (r.contains("zlec")) return ContractType.UZ;
        if (r.contains("dzie")) return ContractType.UOD;

        return null;
    }

    private static SalaryPeriod normalizeSalaryPeriod(String raw) {
        if (raw == null) return SalaryPeriod.MONTH;
        String r = raw.trim().toLowerCase(Locale.ROOT);
        if (r.isBlank()) return SalaryPeriod.MONTH;

        if (r.contains("hour") || r.contains("godz")) return SalaryPeriod.HOUR;
        if (r.contains("year") || r.contains("rok")) return SalaryPeriod.YEAR;
        return SalaryPeriod.MONTH;
    }

    private static Instant parseInstantSafe(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                try {
                    return Instant.parse(c);
                } catch (Exception ignored) {
                }
            }
        }
        return Instant.now();
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    private static String blankToNull(String v) {
        if (v == null) return null;
        return v.isBlank() ? null : v;
    }

    private record ContractInfo(
            Integer salaryMin,
            Integer salaryMax,
            String currency,
            SalaryPeriod salaryPeriod,
            ContractType mainContract,
            Set<ContractType> contracts
    ) {}
}
