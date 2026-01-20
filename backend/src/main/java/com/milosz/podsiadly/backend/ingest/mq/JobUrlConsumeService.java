package com.milosz.podsiadly.backend.ingest.mq;

import com.google.common.util.concurrent.RateLimiter;
import com.milosz.podsiadly.backend.ingest.parser.*;
import com.milosz.podsiadly.backend.ingest.service.NofluffJobsIngestService;
import com.milosz.podsiadly.backend.ingest.service.OfferUpsertService;
import com.milosz.podsiadly.backend.job.domain.JobOffer;
import com.milosz.podsiadly.backend.job.domain.JobSource;
import com.milosz.podsiadly.backend.job.repository.JobOfferRepository;
import com.milosz.podsiadly.backend.job.service.ingest.ExternalJobOfferIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobUrlConsumeService {

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36";

    private static final RateLimiter NFJ_FETCH_LIMITER = RateLimiter.create(2.0d);

    private static final RateLimiter TP_FETCH_LIMITER = RateLimiter.create(1.0d); //2.0d trigger sometimes 429

    private static final Pattern TP_OFFER_ID = Pattern.compile(
            "(?:,oferta,|%2Coferta%2C)([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"
    );

    private final JustJoinParser justJoinParser;
    private final OfferUpsertService upsertService;

    private final NofluffParser nofluffParser;
    private final NofluffJobsIngestService nofluffIngest;
    private final NfjHtmlParser nfjHtmlParser;

    private final TheProtocolParser theProtocolParser;
    private final SolidParser solidParser;
    private final ExternalJobOfferIngestService externalIngest;

    private final JobOfferRepository offers;

    @Value("${ingest.logging.quiet:true}")
    private boolean quietLogging;

    @Transactional
    public void consume(UrlMessage msg) throws Exception {
        final String url = msg.url();
        final JobSource source = (msg.source() != null) ? msg.source() : JobSource.JUSTJOIN;

        log.debug("[ingest] got msg source={} url={}", source, url);

        try {
            dispatchBySource(url, source);
        } catch (HttpStatusException e) {
            handleHttpStatusException(e, url, source);
        } catch (InterruptedIOException e) {
            handleInterruptedIo(url);
        } catch (IOException e) {
            handleIoException(e, url);
        } catch (DataIntegrityViolationException e) {
            handleDataIntegrityViolation(e, url);
        } catch (ImmediateRequeueAmqpException | AmqpRejectAndDontRequeueException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ingest] unexpected error source={} url={}", source, url, e);
            throw new AmqpRejectAndDontRequeueException("Unexpected for " + url, e);
        }
    }

    private void dispatchBySource(String url, JobSource source) throws Exception {
        log.debug("[ingest] dispatch source={} url={}", source, url);

        if (source == JobSource.JUSTJOIN) {
            String html = fetchJustjoinHtml(url);
            handleJustJoin(url, html, source);
            return;
        }

        if (source == JobSource.NOFLUFFJOBS) {
            handleNofluff(url, source);
            return;
        }

        if (source == JobSource.SOLIDJOBS) {
            handleSolid(url, source);
            return;
        }

        if (source == JobSource.THEPROTOCOL) {
            handleTheProtocol(url, source);
            return;
        }

        logDrop("[ingest] unsupported source {} for url {}, drop", source, url);
        throw new AmqpRejectAndDontRequeueException("Unsupported source " + source);
    }

    private void handleHttpStatusException(HttpStatusException e, String url, JobSource source) {
        int sc = e.getStatusCode();

        if (sc == 404 || sc == 410) {
            logGone("[ingest] offer gone ({}): {} → mark inactive", sc, url);
            deactivateGoneOffer(source, url);
            return;
        }

        if (sc == 408 || sc == 425 || sc == 429 || (sc >= 500 && sc < 600)) {
            logRequeue("[ingest] transient HTTP {} for {} (source={}), requeue", sc, url, source);
            throw new ImmediateRequeueAmqpException("HTTP " + sc + " for " + url);
        }

        logDrop("[ingest] non-retryable HTTP {} for {} (source={}), drop", sc, url, source);
        throw new AmqpRejectAndDontRequeueException("Non-retryable HTTP " + sc + " for " + url);
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

    private void handleInterruptedIo(String url) {
        logRequeue("[ingest] I/O timeout/interrupted for {}, requeue", url);
        throw new ImmediateRequeueAmqpException("I/O timeout for " + url);
    }

    private void handleIoException(IOException e, String url) {
        logRequeue("[ingest] I/O error for {}, requeue: {}", url, e.toString());
        throw new ImmediateRequeueAmqpException("I/O error for " + url);
    }

    private void handleDataIntegrityViolation(DataIntegrityViolationException e, String url) {
        if (isSourceExternalUniqueConflict(e)) {
            logOk("[ingest] duplicate / already exists for {}, skipping", url);
            return;
        }
        logDrop("[ingest] DataIntegrityViolation for {}, drop: {}", url, e.getMessage());
        throw new AmqpRejectAndDontRequeueException("DataIntegrityViolation for " + url, e);
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
        String html = fetchNofluffHtml(url);

        if (nfjHtmlParser.isExpired(html)) {
            logGone("[ingest] NFJ expired by banner url={} -> mark inactive", url);
            deactivateGoneOffer(source, url);
            return;
        }

        LocalDate validTo = nfjHtmlParser.extractValidTo(html);

        boolean active = true;
        if (validTo != null) {
            active = !validTo.isBefore(LocalDate.now());
        }

        if (!active) {
            logGone("[ingest] NFJ expired by HTML validTo={} url={} -> mark inactive", validTo, url);
            deactivateGoneOffer(source, url);
            return;
        }

        String json = fetchNofluffJson(externalId);
        var dto = nofluffParser.parseFromApiJson(externalId, json, url);

        nofluffIngest.importSingle(dto);
        logOk("[ingest] NFJ upsert OK: {}", url);
    }

    private String fetchNofluffJson(String externalId) throws IOException {
        String apiUrl = "https://nofluffjobs.com/api/posting/" + externalId
                + "?salaryCurrency=PLN&salaryPeriod=month&region=pl&language=pl-PL";

        NFJ_FETCH_LIMITER.acquire();

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

    private void handleSolid(String url, JobSource source) throws IOException {
        String externalId = solidIdFromOfferUrl(url);
        String apiPath = solidApiPathFromOfferUrl(url);
        String apiUrl = "https://solid.jobs/api/offers/" + apiPath;

        log.debug("[ingest] SOLID fetch apiUrl={} (externalId={})", apiUrl, externalId);

        String json = Jsoup.connect(apiUrl)
                .ignoreContentType(true)
                .timeout(15_000)
                .header("Accept", "application/vnd.solidjobs.jobofferdetails+json, application/json, */*")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", url)
                .userAgent(BROWSER_UA)
                .get()
                .body()
                .text();

        var dto = solidParser.parseFromApiJson(url, externalId, json);
        if (dto == null) {
            logDrop("[ingest] SOLID invalid JSON for {}, skipping", url);
            return;
        }

        if (dto.getDivision() == null || !dto.getDivision().equalsIgnoreCase("IT")) {
            logOk("[ingest] SOLID skip non-IT offer (division={}): {}", dto.getDivision(), url);
            return;
        }

        var data = SolidOfferMapper.map(dto);
        externalIngest.ingest(source, externalId, data);
        logOk("[ingest] SOLID upsert OK: {}", url);
    }

    private void handleTheProtocol(String url, JobSource source) throws IOException {
        log.info("[theprotocol] start url={} source={}", url, source);

        String offerId = extractTheProtocolOfferId(url);
        if (offerId == null) {
            logDrop("[theprotocol] cannot extract offerId from url={}, drop", url);
            throw new AmqpRejectAndDontRequeueException("Cannot extract offerId from url " + url);
        }

        String apiUrl = "https://apus-api.theprotocol.it/offers/" + offerId;

        TP_FETCH_LIMITER.acquire();

        log.debug("[theprotocol] fetch apiUrl={} (offerId={})", apiUrl, offerId);

        String json = Jsoup.connect(apiUrl)
                .ignoreContentType(true)
                .timeout(15_000)
                .userAgent(BROWSER_UA)
                .referrer("https://theprotocol.it/")
                .header("Accept", "application/json, text/plain, */*")
                .header("Origin", "https://theprotocol.it")
                .header("Referer", "https://theprotocol.it/")
                .header("Accept-Language", "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7")
                .followRedirects(true)
                .get()
                .body()
                .text();

        log.debug("[theprotocol] fetched api bytes={} offerId={}", json != null ? json.length() : -1, offerId);

        final TheProtocolParser.Parsed p;
        try {
            p = theProtocolParser.parseFromApiJson(url, offerId, json);
            log.info("[theprotocol] parsed ok offerId={} title={} company={}",
                    offerId, p.title(), p.companyName());
        } catch (Exception e) {
            log.warn("[theprotocol] parse failed offerId={} url={} err={}", offerId, url, e.toString());
            throw e;
        }

        String externalId = offerId;

        var data = new com.milosz.podsiadly.backend.job.service.ingest.ExternalJobOfferData(
                p.title(),
                p.description(),
                p.companyName(),
                p.cityName(),
                p.remote(),
                p.level(),
                p.mainContract(),
                p.contracts(),
                p.salaryMin(),
                p.salaryMax(),
                p.currency(),
                p.salaryPeriod(),
                p.detailsUrl(),
                p.applyUrl(),
                p.techTags(),
                p.techStack(),
                p.publishedAt(),
                p.active()
        );

        externalIngest.ingest(source, externalId, data);
        logOk("[ingest] THEPROTOCOL upsert OK: {}", url);
    }

    private static String extractTheProtocolOfferId(String url) {
        if (url == null) return null;

        Matcher m1 = TP_OFFER_ID.matcher(url);
        if (m1.find()) return m1.group(1);

        String decoded;
        try {
            decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            decoded = url;
        }

        Matcher m2 = TP_OFFER_ID.matcher(decoded);
        if (m2.find()) return m2.group(1);

        int idx = decoded.lastIndexOf(',');
        if (idx >= 0 && idx + 1 < decoded.length()) {
            String tail = decoded.substring(idx + 1).trim();
            if (tail.matches("[0-9a-fA-F\\-]{36}")) return tail;
        }

        return null;
    }

    private static String solidIdFromOfferUrl(String url) {
        String[] parts = url.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            String p = parts[i];
            if (p != null && !p.isBlank() && p.chars().allMatch(Character::isDigit)) {
                return p;
            }
        }
        return lastPath(url);
    }

    private static String solidApiPathFromOfferUrl(String url) {
        String marker = "/offer/";
        int idx = url.indexOf(marker);
        if (idx < 0) return lastPath(url);
        return url.substring(idx + marker.length());
    }

    private String fetchNofluffHtml(String url) throws IOException {
        NFJ_FETCH_LIMITER.acquire();
        return Jsoup.connect(url)
                .userAgent(BROWSER_UA)
                .referrer("https://nofluffjobs.com/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7")
                .followRedirects(true)
                .timeout(15_000)
                .get()
                .outerHtml();
    }

    private void deactivateGoneOffer(JobSource source, String url) {
        JobSource safeSource = (source != null) ? source : JobSource.JUSTJOIN;

        String normUrl = normalizeUrl(url);
        String externalId = lastPath(normUrl);

        var opt = offers.findBySourceAndExternalId(safeSource, externalId);
        if (opt.isEmpty()) {
            opt = offers.findFirstBySourceAndUrl(safeSource, normUrl);
        }

        if (opt.isEmpty()) {
            log.warn("[ingest] deactivate: offer not found in DB source={} externalId={} url={}",
                    safeSource, externalId, normUrl);
            return;
        }

        JobOffer e = opt.get();
        if (Boolean.FALSE.equals(e.getActive())) return;

        e.setActive(false);
        offers.save(e);
    }

    private static String normalizeUrl(String url) {
        if (url == null) return null;
        int q = url.indexOf('?');
        if (q >= 0) url = url.substring(0, q);
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    private void logOk(String fmt, Object... args)      { if (quietLogging) log.debug(fmt, args); else log.info(fmt, args); }
    private void logGone(String fmt, Object... args)    { if (quietLogging) log.debug(fmt, args); else log.info(fmt, args); }
    private void logRequeue(String fmt, Object... args) { if (quietLogging) log.debug(fmt, args); else log.warn(fmt, args); }
    private void logDrop(String fmt, Object... args)    { if (quietLogging) log.debug(fmt, args); else log.warn(fmt, args); }

    private static String lastPath(String url) {
        if (url == null) return null;
        int q = url.indexOf('?');
        if (q >= 0) url = url.substring(0, q);
        int i = url.lastIndexOf('/');
        return i >= 0 ? url.substring(i + 1) : url;
    }

    private boolean isSourceExternalUniqueConflict(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();
        String msg = (cause != null ? cause.getMessage() : e.getMessage());
        if (msg == null) return false;
        return msg.toLowerCase().contains("ux_job_offer_source_external");
    }
}
