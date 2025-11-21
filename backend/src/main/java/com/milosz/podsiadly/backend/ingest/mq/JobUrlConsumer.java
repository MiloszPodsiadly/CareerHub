package com.milosz.podsiadly.backend.ingest.mq;

import com.milosz.podsiadly.backend.ingest.parser.JustJoinParser;
import com.milosz.podsiadly.backend.ingest.parser.NofluffParser;
import com.milosz.podsiadly.backend.ingest.service.NofluffJobsIngestService;
import com.milosz.podsiadly.backend.ingest.service.OfferUpsertService;
import com.milosz.podsiadly.backend.job.domain.JobSource;
import com.milosz.podsiadly.backend.job.repository.JobOfferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobUrlConsumer {

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36";

    private final JustJoinParser justJoinParser;
    private final OfferUpsertService upsertService;
    private final NofluffParser nofluffParser;
    private final NofluffJobsIngestService nofluffIngest;
    private final JobOfferRepository offers;

    @Value("${ingest.logging.quiet:true}")
    private boolean quietLogging;

    @RabbitListener(
            id = "jobUrlConsumer",
            queues = "#{ingestMessagingProperties.queue.urls}",
            containerFactory = "rabbitListenerContainerFactory",
            concurrency = "4-8",
            autoStartup = "false"
    )
    public void onMessage(UrlMessage msg) throws Exception {
        final String url = msg.url();
        final JobSource source = msg.source() != null ? msg.source() : JobSource.JUSTJOIN;

        try {
            if (source == JobSource.JUSTJOIN) {
                String html = fetchJustjoinHtml(url);
                handleJustJoin(url, html, source);
            } else if (source == JobSource.NOFLUFFJOBS) {
                handleNofluff(url, source);
            } else {
                logDrop("[ingest] unsupported source {} for url {}, drop", source, url);
                throw new AmqpRejectAndDontRequeueException("Unsupported source " + source);
            }

        } catch (HttpStatusException e) {
            int sc = e.getStatusCode();
            if (sc == 404 || sc == 410) {
                logGone("[ingest] offer gone ({}): {} → mark inactive (archiver moves it later)", sc, url);
                deactivateGoneOffer(source, url);
                return;
            }
            if (sc == 408 || sc == 425 || sc == 429 || (sc >= 500 && sc < 600)) {
                logRequeue("[ingest] transient HTTP {} for {}, requeue", sc, url);
                throw new ImmediateRequeueAmqpException("HTTP " + sc + " for " + url);
            }
            logDrop("[ingest] non-retryable HTTP {} for {}, drop", sc, url);
            throw new AmqpRejectAndDontRequeueException("Non-retryable HTTP " + sc + " for " + url);

        } catch (InterruptedIOException e) {
            logRequeue("[ingest] I/O timeout/interrupted for {}, requeue", url);
            throw new ImmediateRequeueAmqpException("I/O timeout for " + url);

        } catch (IOException e) {
            logRequeue("[ingest] I/O error for {}, requeue: {}", url, e.toString());
            throw new ImmediateRequeueAmqpException("I/O error for " + url);

        } catch (DataIntegrityViolationException e) {
            logDrop("[ingest] unique-conflict (already exists) for {}, drop: {}", url, e.getMessage());
            throw new AmqpRejectAndDontRequeueException("unique conflict, already exists: " + url, e);

        } catch (Exception e) {
            log.error("[ingest] unexpected error for {}", url, e);
            throw new AmqpRejectAndDontRequeueException("Unexpected for " + url, e);
        }
    }


    private void handleJustJoin(String url, String html, JobSource source) {
        if (justJoinParser.isExpiredPage(url, html)) {
            logGone("[ingest] JJ expired(200) page: {} → mark inactive", url);
            deactivateGoneOffer(source, url);
            return;
        }
        var parsed = justJoinParser.parse(url, html);
        upsertService.upsert(parsed);
        logOk("[ingest] JJ upsert OK: {}", url);
    }

    private String fetchJustjoinHtml(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent(BROWSER_UA)
                .referrer("https://justjoin.it/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7")
                .followRedirects(true)
                .timeout(15_000)
                .get()
                .outerHtml();
    }


    private void handleNofluff(String url, JobSource source) throws IOException {
        String externalId = lastPath(url);

        String json = fetchNofluffJson(externalId);
        var dto = nofluffParser.parseFromApiJson(externalId, json, url);

        nofluffIngest.importSingle(dto);
        logOk("[ingest] NFJ upsert OK: {}", url);
    }

    private String fetchNofluffJson(String externalId) throws IOException {
        // endpoint dokładnie jak widziałeś w DevTools
        String apiUrl = "https://nofluffjobs.com/api/posting/" + externalId
                + "?salaryCurrency=PLN&salaryPeriod=month&region=pl&language=pl-PL";

        return Jsoup.connect(apiUrl)
                .userAgent(BROWSER_UA)
                .referrer("https://nofluffjobs.com/")
                .ignoreContentType(true)
                .header("Accept", "application/json")
                .timeout(15_000)
                .get()
                .body()
                .text();
    }


    private void deactivateGoneOffer(JobSource source, String url) {
        JobSource safeSource = source != null ? source : JobSource.JUSTJOIN;
        String externalId = lastPath(url);
        offers.findBySourceAndExternalId(safeSource, externalId).ifPresent(e -> {
            e.setActive(false);
            e.setLastSeenAt(Instant.now());
            offers.save(e);
        });
    }

    private void logOk(String fmt, Object... args)      { if (quietLogging) log.debug(fmt, args); else log.info(fmt, args); }
    private void logGone(String fmt, Object... args)    { if (quietLogging) log.debug(fmt, args); else log.info(fmt, args); }
    private void logRequeue(String fmt, Object... args) { if (quietLogging) log.debug(fmt, args); else log.warn(fmt, args); }
    private void logDrop(String fmt, Object... args)    { if (quietLogging) log.debug(fmt, args); else log.warn(fmt, args); }

    private static String lastPath(String url) {
        int q = url.indexOf('?');
        if (q >= 0) url = url.substring(0, q);
        int i = url.lastIndexOf('/');
        return i >= 0 ? url.substring(i + 1) : url;
    }
}
