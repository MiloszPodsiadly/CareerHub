package com.milosz.podsiadly.backend.ingest.mq;

import com.milosz.podsiadly.backend.ingest.parser.JustJoinParser;
import com.milosz.podsiadly.backend.ingest.service.OfferUpsertService;
import com.milosz.podsiadly.backend.job.domain.JobOffer;
import com.milosz.podsiadly.backend.job.repository.JobOfferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobUrlConsumer {

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36";

    private final JustJoinParser parser;
    private final OfferUpsertService upsertService;
    private final JobOfferRepository offers;

    @RabbitListener(id = "jobUrlConsumer",
            queues = "#{ingestMessagingProperties.queue.urls}",
            containerFactory = "rabbitListenerContainerFactory",
            autoStartup = "false")
    public void onMessage(UrlMessage msg) throws Exception {
        final String url = msg.url();
        try {
            String html = fetch(url);
            JustJoinParser.ParsedOffer p = parser.parse(url, html);
            upsertService.upsert(p);
        } catch (HttpStatusException e) {
            if (e.getStatusCode() == 404 || e.getStatusCode() == 410) {
                log.info("[ingest] offer gone ({}): {} → deactivating", e.getStatusCode(), url);
                deactivateGoneOffer(msg);
                throw new AmqpRejectAndDontRequeueException("Gone: " + url, e);
            }
            log.warn("[ingest] HTTP {} for {} – will be requeued", e.getStatusCode(), url);
            throw e;
        } catch (Exception e) {
            log.warn("[ingest] failed for {} – will be requeued: {}", url, e.toString());
            throw e;
        }
    }

    private String fetch(String url) throws Exception {
        return Jsoup.connect(url)
                .userAgent(BROWSER_UA)
                .referrer("https://justjoin.it/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7")
                .followRedirects(true)
                .timeout(15000)
                .get()
                .outerHtml();
    }

    private void deactivateGoneOffer(UrlMessage msg) {
        String source = nullSafe(msg.source(), "JUSTJOIN");
        String externalId = lastPath(msg.url());

        offers.findBySourceAndExternalId(source, externalId).ifPresent(e -> {
            e.setActive(false);
            e.setLastSeenAt(Instant.now());
            offers.save(e);
        });
    }

    private static String lastPath(String url) {
        int i = url.lastIndexOf('/');
        return i >= 0 ? url.substring(i + 1) : url;
    }

    private static String nullSafe(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }
}
