package com.milosz.podsiadly.careerhub.agentcrawler.ingest.mq;

import com.google.common.util.concurrent.RateLimiter;
import com.milosz.podsiadly.careerhub.agentcrawler.ingest.model.ExternalJobOfferData;
import com.milosz.podsiadly.careerhub.agentcrawler.ingest.model.ParsedExternalOffer;
import com.milosz.podsiadly.careerhub.agentcrawler.ingest.parser.JustJoinParser;
import com.milosz.podsiadly.careerhub.agentcrawler.ingest.parser.NfjHtmlParser;
import com.milosz.podsiadly.careerhub.agentcrawler.ingest.parser.NofluffParser;
import com.milosz.podsiadly.careerhub.agentcrawler.ingest.parser.SolidOfferMapper;
import com.milosz.podsiadly.careerhub.agentcrawler.ingest.parser.SolidParser;
import com.milosz.podsiadly.careerhub.agentcrawler.ingest.parser.TheProtocolParser;
import com.milosz.podsiadly.careerhub.agentcrawler.job.domain.JobSource;
import com.milosz.podsiadly.careerhub.agentcrawler.mq.ExternalOfferPublisher;
import com.milosz.podsiadly.careerhub.agentcrawler.mq.UrlMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobUrlConsumeService {

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36";

    private static final RateLimiter JJ_FETCH_LIMITER = RateLimiter.create(2.0d);
    private static final RateLimiter TP_FETCH_LIMITER = RateLimiter.create(1.0d);
    private static final RateLimiter SOLID_FETCH_LIMITER = RateLimiter.create(2.0d);
    private static final Pattern TP_OFFER_ID = Pattern.compile(
            "(?:,oferta,|%2Coferta%2C)([0-9a-fA-F\\-]{36})"
    );
    private final RateLimiter nfjFetchLimiter = RateLimiter.create(0.25d);
    private final AtomicLong nfjSuspendedUntilEpochMs = new AtomicLong(0L);

    private final JustJoinParser justJoinParser;
    private final NofluffParser nofluffParser;
    private final NfjHtmlParser nfjHtmlParser;
    private final TheProtocolParser theProtocolParser;
    private final SolidParser solidParser;
    private final ExternalOfferPublisher externalOfferPublisher;

    @Value("${ingest.logging.quiet:true}")
    private boolean quietLogging;

    @Value("${jobs.ingest.nfj.detail-fetch-rate-per-second:0.25}")
    private double nfjDetailFetchRatePerSecond;

    @Value("${jobs.ingest.nfj.cooldown-after-429:PT15M}")
    private Duration nfjCooldownAfter429;

    public void consume(UrlMessage msg) throws Exception {
        final String url = msg.url();
        final JobSource source = parseSource(msg.source());

        log.debug("[ingest] got msg source={} url={}", source, url);

        try {
            dispatchBySource(url, source);
        } catch (HttpStatusException e) {
            handleHttpStatusException(e, url, source);
        } catch (InterruptedIOException e) {
            handleInterruptedIo(url);
        } catch (IOException e) {
            handleIoException(e, url);
        } catch (ImmediateRequeueAmqpException | AmqpRejectAndDontRequeueException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ingest] unexpected error source={} url={}", source, url, e);
            throw new AmqpRejectAndDontRequeueException("Unexpected for " + url, e);
        }
    }

    private JobSource parseSource(String raw) {
        if (raw == null || raw.isBlank()) {
            return JobSource.JUSTJOIN;
        }
        return JobSource.valueOf(raw.trim().toUpperCase());
    }

    private void dispatchBySource(String url, JobSource source) throws Exception {
        switch (source) {
            case JUSTJOIN -> handleJustJoin(url);
            case NOFLUFFJOBS -> handleNofluff(url);
            case SOLIDJOBS -> handleSolid(url);
            case THEPROTOCOL -> handleTheProtocol(url);
            case PRACUJ -> {
                logDrop("[ingest] PRACUJ URL message ignored (handled directly in agent). url={}", url);
                throw new AmqpRejectAndDontRequeueException("PRACUJ URL messages are not processed by raw-url consumer");
            }
        }
    }

    private void handleHttpStatusException(HttpStatusException e, String url, JobSource source) {
        int sc = e.getStatusCode();

        if (sc == 404 || sc == 410) {
            logGone("[ingest] offer gone ({}): {} -> mark inactive", sc, url);
            publishInactive(source, externalIdFor(source, url), normalizeUrl(url));
            return;
        }

        if (source == JobSource.NOFLUFFJOBS && sc == 429) {
            activateNfjCooldown(url);
            logRequeue("[ingest] NFJ rate limited HTTP {} for {}, long backoff", sc, url);
            throw new NfjRateLimitException("NFJ rate limit HTTP " + sc + " for " + url);
        }

        if (sc == 408 || sc == 425 || sc == 429 || (sc >= 500 && sc < 600)) {
            logRequeue("[ingest] transient HTTP {} for {} (source={}), requeue", sc, url, source);
            throw new ImmediateRequeueAmqpException("HTTP " + sc + " for " + url);
        }

        logDrop("[ingest] non-retryable HTTP {} for {} (source={}), drop", sc, url, source);
        throw new AmqpRejectAndDontRequeueException("Non-retryable HTTP " + sc + " for " + url);
    }

    private void handleJustJoin(String url) throws IOException {
        String html = fetchHtml(url, "https://justjoin.it/");
        if (justJoinParser.isExpiredPage(url, html)) {
            publishInactive(JobSource.JUSTJOIN, lastPath(url), normalizeUrl(url));
            return;
        }

        externalOfferPublisher.publish(ExternalOfferMessageMapper.fromJustJoin(justJoinParser.parse(url, html)));
        logOk("[ingest] JJ publish OK: {}", url);
    }

    private void handleNofluff(String url) throws IOException {
        String externalId = lastPath(url);
        ensureNfjNotSuspended(url);
        String html = fetchNofluffHtml(url, externalId);

        if (nfjHtmlParser.isExpired(html)) {
            publishInactive(JobSource.NOFLUFFJOBS, externalId, normalizeUrl(url));
            return;
        }

        LocalDate validTo = nfjHtmlParser.extractValidTo(html);
        if (validTo != null && validTo.isBefore(LocalDate.now())) {
            publishInactive(JobSource.NOFLUFFJOBS, externalId, normalizeUrl(url));
            return;
        }

        ensureNfjNotSuspended(url);
        String json = fetchNofluffJson(externalId, url);
        ParsedExternalOffer parsedOffer = nofluffParser.parseFromApiJson(externalId, json, url);
        externalOfferPublisher.publish(ExternalOfferMessageMapper.fromParsedOffer(parsedOffer));
        logOk("[ingest] NFJ publish OK: {}", url);
    }

    private void handleSolid(String url) throws IOException {
        String externalId = solidIdFromOfferUrl(url);
        String apiUrl = "https://solid.jobs/api/offers/" + solidApiPathFromOfferUrl(url);

        SOLID_FETCH_LIMITER.acquire();

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

        ExternalJobOfferData data = SolidOfferMapper.map(dto);
        externalOfferPublisher.publish(ExternalOfferMessageMapper.fromData(JobSource.SOLIDJOBS, externalId, data));
        logOk("[ingest] SOLID publish OK: {}", url);
    }

    private void handleTheProtocol(String url) throws IOException {
        String offerId = extractTheProtocolOfferId(url);
        if (offerId == null) {
            logDrop("[theprotocol] cannot extract offerId from url={}, drop", url);
            throw new AmqpRejectAndDontRequeueException("Cannot extract offerId from url " + url);
        }

        String apiUrl = "https://apus-api.theprotocol.it/offers/" + offerId;
        TP_FETCH_LIMITER.acquire();

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

        var parsed = theProtocolParser.parseFromApiJson(url, offerId, json);
        externalOfferPublisher.publish(ExternalOfferMessageMapper.fromParsedOffer(
                new ParsedExternalOffer(
                        JobSource.THEPROTOCOL,
                        parsed.externalId(),
                        new ExternalJobOfferData(
                                parsed.title(),
                                parsed.description(),
                                parsed.companyName(),
                                parsed.cityName(),
                                parsed.remote(),
                                parsed.level(),
                                parsed.mainContract(),
                                parsed.contracts(),
                                parsed.salaryMin(),
                                parsed.salaryMax(),
                                parsed.currency(),
                                parsed.salaryPeriod(),
                                parsed.detailsUrl(),
                                parsed.applyUrl(),
                                parsed.techTags(),
                                parsed.techStack(),
                                parsed.publishedAt(),
                                parsed.active()
                        )
                )
        ));
        logOk("[ingest] THEPROTOCOL publish OK: {}", url);
    }

    private void publishInactive(JobSource source, String externalId, String url) {
        externalOfferPublisher.publish(ExternalOfferMessageMapper.inactive(source, externalId, url));
        logGone("[ingest] {} inactive published externalId={} url={}", source, externalId, url);
    }

    private String fetchHtml(String url, String referrer) throws IOException {
        if ("https://justjoin.it/".equals(referrer)) {
            JJ_FETCH_LIMITER.acquire();
        }
        return Jsoup.connect(url)
                .userAgent(BROWSER_UA)
                .referrer(referrer)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7")
                .followRedirects(true)
                .timeout(15_000)
                .get()
                .outerHtml();
    }

    private String fetchNofluffHtml(String url, String externalId) throws IOException {
        nfjFetchLimiter.setRate(nfjDetailFetchRatePerSecond);
        nfjFetchLimiter.acquire();
        long startedAt = System.currentTimeMillis();
        try {
            String html = fetchHtml(url, "https://nofluffjobs.com/");
            log.debug("[ingest] NFJ html fetch OK externalId={} tookMs={} url={}",
                    externalId, System.currentTimeMillis() - startedAt, url);
            return html;
        } catch (HttpStatusException ex) {
            log.warn("[ingest] NFJ html fetch HTTP {} externalId={} tookMs={} url={}",
                    ex.getStatusCode(), externalId, System.currentTimeMillis() - startedAt, url);
            throw ex;
        } catch (IOException ex) {
            log.warn("[ingest] NFJ html fetch I/O error externalId={} tookMs={} url={} error={}",
                    externalId, System.currentTimeMillis() - startedAt, url, ex.toString());
            throw ex;
        }
    }

    private String fetchNofluffJson(String externalId, String url) throws IOException {
        String apiUrl = "https://nofluffjobs.com/api/posting/" + externalId
                + "?salaryCurrency=PLN&salaryPeriod=month&region=pl&language=pl-PL";

        nfjFetchLimiter.setRate(nfjDetailFetchRatePerSecond);
        nfjFetchLimiter.acquire();

        long startedAt = System.currentTimeMillis();
        try {
            String json = Jsoup.connect(apiUrl)
                    .userAgent(BROWSER_UA)
                    .referrer("https://nofluffjobs.com/")
                    .ignoreContentType(true)
                    .header("Accept", "application/json")
                    .timeout(15_000)
                    .get()
                    .body()
                    .text();
            log.debug("[ingest] NFJ json fetch OK externalId={} tookMs={} url={}",
                    externalId, System.currentTimeMillis() - startedAt, url);
            return json;
        } catch (HttpStatusException ex) {
            log.warn("[ingest] NFJ json fetch HTTP {} externalId={} tookMs={} url={}",
                    ex.getStatusCode(), externalId, System.currentTimeMillis() - startedAt, url);
            throw ex;
        } catch (IOException ex) {
            log.warn("[ingest] NFJ json fetch I/O error externalId={} tookMs={} url={} error={}",
                    externalId, System.currentTimeMillis() - startedAt, url, ex.toString());
            throw ex;
        }
    }

    private void handleInterruptedIo(String url) {
        logRequeue("[ingest] I/O timeout/interrupted for {}, requeue", url);
        throw new ImmediateRequeueAmqpException("I/O timeout for " + url);
    }

    private void handleIoException(IOException e, String url) {
        logRequeue("[ingest] I/O error for {}, requeue: {}", url, e.toString());
        throw new ImmediateRequeueAmqpException("I/O error for " + url);
    }

    private void ensureNfjNotSuspended(String url) {
        long suspendedUntil = nfjSuspendedUntilEpochMs.get();
        long now = System.currentTimeMillis();
        if (suspendedUntil <= now) {
            return;
        }

        long remainingMs = suspendedUntil - now;
        logRequeue("[ingest] NFJ cooldown active remainingMs={} for {}", remainingMs, url);
        throw new NfjRateLimitException("NFJ cooldown active for " + remainingMs + "ms for " + url);
    }

    private void activateNfjCooldown(String url) {
        long cooldownMs = Math.max(1_000L, nfjCooldownAfter429.toMillis());
        long newSuspendedUntil = System.currentTimeMillis() + cooldownMs;
        long effectiveSuspendedUntil = nfjSuspendedUntilEpochMs.updateAndGet(current ->
                Math.max(current, newSuspendedUntil));
        log.warn("[ingest] NFJ cooldown activated untilEpochMs={} durationMs={} url={}",
                effectiveSuspendedUntil,
                cooldownMs,
                url);
    }

    private String externalIdFor(JobSource source, String url) {
        if (source == JobSource.THEPROTOCOL) {
            String offerId = extractTheProtocolOfferId(url);
            if (offerId != null) {
                return offerId;
            }
        }
        if (source == JobSource.SOLIDJOBS) {
            return solidIdFromOfferUrl(url);
        }
        return lastPath(url);
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

    private void logOk(String fmt, Object... args) {
        if (quietLogging) log.debug(fmt, args); else log.info(fmt, args);
    }

    private void logGone(String fmt, Object... args) {
        if (quietLogging) log.debug(fmt, args); else log.info(fmt, args);
    }

    private void logRequeue(String fmt, Object... args) {
        if (quietLogging) log.debug(fmt, args); else log.warn(fmt, args);
    }

    private void logDrop(String fmt, Object... args) {
        if (quietLogging) log.debug(fmt, args); else log.warn(fmt, args);
    }

    private static String normalizeUrl(String url) {
        if (url == null) return null;
        int q = url.indexOf('?');
        if (q >= 0) url = url.substring(0, q);
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    private static String lastPath(String url) {
        String normalized = normalizeUrl(url);
        if (normalized == null) return null;
        int i = normalized.lastIndexOf('/');
        return i >= 0 ? normalized.substring(i + 1) : normalized;
    }
}
