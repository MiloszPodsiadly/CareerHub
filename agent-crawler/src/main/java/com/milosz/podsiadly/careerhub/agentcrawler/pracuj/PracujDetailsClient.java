package com.milosz.podsiadly.careerhub.agentcrawler.pracuj;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import com.milosz.podsiadly.careerhub.agentcrawler.config.PlaywrightHub;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PracujDetailsClient {

    private final PlaywrightHub hub;

    private static final int NAV_TIMEOUT_MS = 45_000;
    private static final int NEXT_DATA_TIMEOUT_MS = 25_000;

    public String fetchOfferHtml(String offerUrl) {
        BrowserContext ctx = newContext();
        Page page = ctx.newPage();

        try {
            page.setDefaultTimeout(NAV_TIMEOUT_MS);
            page.setDefaultNavigationTimeout(NAV_TIMEOUT_MS);

            Response nav = page.navigate(
                    offerUrl,
                    new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
            );

            if (nav != null) {
                log.info("[pracuj-details] navigate status={} url={}", nav.status(), offerUrl);
            }

            acceptCookiesIfPresent(page);

            String title = safe(page::title);
            if (looksLikeBotWall(title, page.url())) {
                dumpDebugArtifacts(page);
                throw new PlaywrightException("BotWall suspected (title=" + title + ", url=" + page.url() + ")");
            }

            waitForNextDataScript(page, NEXT_DATA_TIMEOUT_MS);

            String html = page.content();
            log.info("[pracuj-details] fetched html bytes={} url={}", html.length(), page.url());

            if (!html.contains("id=\"__NEXT_DATA__\"")) {
                dumpDebugArtifacts(page);
                throw new IllegalStateException("No __NEXT_DATA__ found in HTML after wait");
            }

            return html;

        } catch (Exception e) {
            log.warn("[pracuj-details] FAILED url={} finalUrl={} err={}",
                    offerUrl, safe(page::url), e.toString());
            dumpDebugArtifacts(page);
            throw e;
        } finally {
            try { page.close(); } catch (Exception ignored) {}
            try { ctx.close(); } catch (Exception ignored) {}
        }
    }

    private void waitForNextDataScript(Page page, long timeoutMs) {
        page.waitForSelector(
                "script#__NEXT_DATA__",
                new Page.WaitForSelectorOptions()
                        .setTimeout(timeoutMs)
                        .setState(WaitForSelectorState.ATTACHED)
        );

        page.waitForFunction(
                "() => {" +
                        "  const el = document.querySelector('script#__NEXT_DATA__');" +
                        "  const txt = el && el.textContent ? el.textContent.trim() : '';" +
                        "  return txt.length > 50 && (txt.startsWith('{') || txt.startsWith('['));" +
                        "}",
                null,
                new Page.WaitForFunctionOptions().setTimeout(timeoutMs)
        );
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
                .setExtraHTTPHeaders(Map.of(
                        "Accept-Language", "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7",
                        "Upgrade-Insecure-Requests", "1"
                ))
        );
    }

    private void acceptCookiesIfPresent(Page page) {
        try {
            Locator btn = page.locator("button:has-text(\"Akceptuj\"), button:has-text(\"Zaakceptuj\")");
            if (btn.count() > 0) {
                btn.first().click(new Locator.ClickOptions().setTimeout(3_000));
                log.info("[pracuj-details] cookies accepted");
            }
        } catch (Exception ignored) {}
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

    private void dumpDebugArtifacts(Page page) {
        try {
            String ts = String.valueOf(Instant.now().toEpochMilli());
            Path png = Path.of("/tmp/pracuj-details-" + ts + ".png");
            Path html = Path.of("/tmp/pracuj-details-" + ts + ".html");

            page.screenshot(new Page.ScreenshotOptions().setPath(png).setFullPage(true));
            Files.writeString(html, page.content());

            log.warn("[pracuj-details] debug saved screenshot={} html={}", png, html);
        } catch (Exception ex) {
            log.warn("[pracuj-details] debug dump failed err={}", ex.toString());
        }
    }

    private static <T> T safe(java.util.concurrent.Callable<T> c) {
        try { return c.call(); } catch (Exception e) { return null; }
    }
}
