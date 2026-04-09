package com.milosz.podsiadly.careerhub.agentcrawler.pracuj;

import com.google.common.util.concurrent.RateLimiter;
import com.milosz.podsiadly.careerhub.agentcrawler.ingest.model.ParsedExternalOffer;
import com.milosz.podsiadly.careerhub.agentcrawler.ingest.mq.ExternalOfferMessageMapper;
import com.milosz.podsiadly.careerhub.agentcrawler.ingest.parser.PracujParser;
import com.milosz.podsiadly.careerhub.agentcrawler.mq.ExternalOfferMessage;
import com.milosz.podsiadly.careerhub.agentcrawler.mq.ExternalOfferPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class PracujCrawlerScheduler {

    private final PracujListingClient listingClient;
    private final PracujDetailsClient detailsClient;
    private final PracujParser parser;
    private final ExternalOfferPublisher publisher;

    private static final RateLimiter LISTING_LIMITER = RateLimiter.create(1.5d);
    private static final RateLimiter DETAILS_LIMITER = RateLimiter.create(2.0d);

    private static final List<String> ITS = List.of(
            "backend","frontend","fullstack","mobile","architecture","devops",
            "gamedev","data-analytics-and-bi","big-data-science","embedded",
            "testing","security","helpdesk","product-management","agile",
            "ux-ui","business-analytics","system-analytics","sap-erp",
            "it-admin","ai-ml"
    );

    private static final int MAX_PAGES_PER_ITS = 80;

    @Scheduled(
            initialDelayString = "${agent.pracuj.initial-delay-ms:15000}",
            fixedDelayString   = "${agent.pracuj.interval-ms:86400000}"
    )
    public void runPeriodic() {
        log.info("[agent-pracuj] periodic crawl triggered");
        runOnce();
    }

    public void runOnce() {
        Map<String, String> firstSeenById = new LinkedHashMap<>(32_768);

        long pagesFetched = 0;
        long offersPublished = 0;
        long duplicates = 0;
        long failedDetails = 0;

        for (String its : ITS) {
            log.info("[agent-pracuj] start its={}", its);

            for (int page = 1; page <= MAX_PAGES_PER_ITS; page++) {
                String listingUrl = buildListingUrl(its, page);

                LISTING_LIMITER.acquire();
                Set<String> urls;

                try {
                    urls = listingClient.fetchOfferUrlsFromListing(listingUrl);
                    pagesFetched++;
                } catch (Exception e) {
                    log.warn("[agent-pracuj] listing FAILED its={} page={} url={} err={}",
                            its, page, listingUrl, e.toString());
                    break;
                }

                if (urls.isEmpty()) {
                    log.info("[agent-pracuj] empty listing -> stop its={} page={}", its, page);
                    break;
                }

                int publishedThisPage = 0;

                for (String url : urls) {
                    String offerIdFromUrl = PracujUrlUtil.extractOfferId(url);
                    if (offerIdFromUrl == null) continue;

                    String prev = firstSeenById.putIfAbsent(offerIdFromUrl, url);
                    if (prev != null) {
                        duplicates++;
                        continue;
                    }

                    DETAILS_LIMITER.acquire();

                    try {
                        String html = detailsClient.fetchOfferHtml(url);

                        ParsedExternalOffer parsed = parser.parse(url, html)
                                .withExternalId(nonBlank(offerIdFromUrl, null));
                        ExternalOfferMessage msg = ExternalOfferMessageMapper.fromParsedOffer(parsed);

                        publisher.publish(msg);

                        offersPublished++;
                        publishedThisPage++;

                        if (offersPublished % 1000 == 0) {
                            log.info("[agent-pracuj] offers published so far={}", offersPublished);
                        }

                    } catch (Exception e) {
                        failedDetails++;
                        log.warn("[agent-pracuj] DETAILS FAILED id={} url={} err={}",
                                offerIdFromUrl, url, e.toString());
                    }
                }

                log.info("[agent-pracuj] its={} page={} urls={} published={}",
                        its, page, urls.size(), publishedThisPage);

                if (publishedThisPage == 0 && page >= 3) {
                    log.info("[agent-pracuj] no new offers -> stop early its={} page={}", its, page);
                    break;
                }
            }
        }

        log.info("====== PRACUJ RUN COMPLETE ======");
        log.info("pagesFetched     = {}", pagesFetched);
        log.info("offersPublished  = {}", offersPublished);
        log.info("duplicates       = {}", duplicates);
        log.info("detailsFailed    = {}", failedDetails);
        log.info("================================");
    }

    private static String nonBlank(String v, String def) {
        return (v != null && !v.isBlank()) ? v : def;
    }

    private static String buildListingUrl(String its, int page) {
        return "https://it.pracuj.pl/praca?its=" + its + "&pn=" + page;
    }
}
