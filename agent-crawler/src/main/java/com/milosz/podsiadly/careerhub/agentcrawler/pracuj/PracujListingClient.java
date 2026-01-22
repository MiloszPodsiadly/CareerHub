package com.milosz.podsiadly.careerhub.agentcrawler.pracuj;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import com.milosz.podsiadly.careerhub.agentcrawler.config.PlaywrightHub;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class PracujListingClient {

    private final PlaywrightHub hub;

    private static final String[] OFFER_SELECTORS = new String[] {
            "a[data-test=\"offer-title-link\"]",
            "a[href*=\",oferta,\"]",
            "a[href*=\"%2Coferta%2C\"]",
            "a[href*=\"oferta\"]"
    };

    public Set<String> fetchOfferUrlsFromListing(String listingUrl) {
        BrowserContext ctx = newContext();
        Page page = ctx.newPage();

        page.onResponse(resp -> {
            try {
                if ("document".equals(resp.request().resourceType())) {
                    log.info("[pracuj] DOC response status={} url={}", resp.status(), resp.url());
                }
            } catch (Exception ignored) {}
        });

        try {
            log.info("[pracuj] goto {}", listingUrl);

            page.setDefaultTimeout(45_000);
            page.setDefaultNavigationTimeout(45_000);

            Response nav = page.navigate(
                    listingUrl,
                    new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
            );

            log.info("[pracuj] navigated -> status={} finalUrl={}",
                    nav != null ? nav.status() : -1,
                    page.url()
            );

            page.waitForLoadState(LoadState.NETWORKIDLE);

            acceptCookiesIfPresent(page);

            String title = safe(page::title);
            log.info("[pracuj] title='{}'", title);

            if (looksLikeBotWall(title, page.url())) {
                throw new PlaywrightException("BotWall/Challenge suspected (title=" + title + ", url=" + page.url() + ")");
            }

            boolean found = waitForAnyOfferSelector(page, 25_000);
            if (!found) {
                throw new TimeoutError("No offer selector visible after wait (url=" + page.url() + ")");
            }

            Set<String> out = new LinkedHashSet<>();
            for (ElementHandle a : page.querySelectorAll("a[href]")) {
                String href = a.getAttribute("href");
                if (href == null) continue;

                String abs = PracujUrlUtil.toAbs(href);
                if (abs == null) continue;

                String id = PracujUrlUtil.extractOfferId(abs);
                if (id == null) continue;

                out.add(PracujUrlUtil.normalize(abs));
            }

            log.info("[pracuj] extracted offers={} (url={})", out.size(), listingUrl);
            if (out.isEmpty()) {
                dumpDebugArtifacts(page, "pracuj-empty");
            }

            return out;

        } catch (PlaywrightException e) {
            log.warn("[pracuj] listing FAILED url={} finalUrl={} err={}",
                    listingUrl, safe(page::url), e.toString());

            dumpDebugArtifacts(page, "pracuj-fail");
            throw e;
        } finally {
            try { page.close(); } catch (Exception ignored) {}
            try { ctx.close(); } catch (Exception ignored) {}
        }
    }

    private BrowserContext newContext() {
        return hub.getBrowser().newContext(new Browser.NewContextOptions()
                .setViewportSize(1366, 768)
                .setLocale("pl-PL")
                .setTimezoneId("Europe/Warsaw")
                .setUserAgent(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36"
                )
                .setExtraHTTPHeaders(java.util.Map.of(
                        "Accept-Language", "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7",
                        "Upgrade-Insecure-Requests", "1"
                ))
        );
    }

    private void acceptCookiesIfPresent(Page page) {
        try {
            Locator btn = page.locator("button:has-text(\"Akceptuj\")");
            if (btn.count() > 0) {
                log.info("[pracuj] accepting cookies");
                btn.first().click(new Locator.ClickOptions().setTimeout(3_000));
            }
        } catch (Exception ignored) {}
    }

    private boolean waitForAnyOfferSelector(Page page, long timeoutMs) {
        long start = System.currentTimeMillis();
        for (;;) {
            for (String sel : OFFER_SELECTORS) {
                try {
                    Locator loc = page.locator(sel);
                    if (loc.count() > 0 && loc.first().isVisible()) {
                        log.info("[pracuj] selector OK -> {}", sel);
                        return true;
                    }
                } catch (Exception ignored) {}
            }
            if (System.currentTimeMillis() - start > timeoutMs) {
                log.warn("[pracuj] none of offer selectors became visible within {}ms", timeoutMs);
                return false;
            }
            page.waitForTimeout(500);
        }
    }

    private boolean looksLikeBotWall(String title, String url) {
        String t = title != null ? title.toLowerCase() : "";
        String u = url != null ? url.toLowerCase() : "";
        return t.contains("attention required")
                || t.contains("access denied")
                || t.contains("just a moment")
                || u.contains("/cdn-cgi/")
                || u.contains("challenge");
    }

    private void dumpDebugArtifacts(Page page, String prefix) {
        try {
            String ts = String.valueOf(Instant.now().toEpochMilli());
            Path png = Path.of("/tmp/" + prefix + "-" + ts + ".png");
            Path html = Path.of("/tmp/" + prefix + "-" + ts + ".html");

            page.screenshot(new Page.ScreenshotOptions().setPath(png).setFullPage(true));
            Files.writeString(html, page.content());

            log.warn("[pracuj] debug saved screenshot={} html={}", png, html);
        } catch (Exception ex) {
            log.warn("[pracuj] debug dump failed err={}", ex.toString());
        }
    }

    private static <T> T safe(java.util.concurrent.Callable<T> c) {
        try { return c.call(); } catch (Exception e) { return null; }
    }
}
