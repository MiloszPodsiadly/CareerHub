package com.milosz.podsiadly.careerhub.agentcrawler.config;

import com.microsoft.playwright.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class PlaywrightHub {

    private Playwright playwright;

    @Getter
    private Browser browser;

    @Value("${agent.playwright.headless:true}")
    private boolean headless;

    @PostConstruct
    public void start() {
        String browsersPath = System.getenv("PLAYWRIGHT_BROWSERS_PATH");
        log.info("[pw] starting chromium headless={} PLAYWRIGHT_BROWSERS_PATH={}", headless, browsersPath);

        playwright = Playwright.create();

        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setArgs(List.of(
                        "--no-sandbox",
                        "--disable-dev-shm-usage",
                        "--disable-gpu",
                        "--disable-blink-features=AutomationControlled"
                ))
        );

        log.info("[pw] started chromium");
    }

    @PreDestroy
    public void stop() {
        try {
            if (browser != null) browser.close();
        } catch (Exception e) {
            log.warn("[pw] browser close failed: {}", e.toString());
        }
        try {
            if (playwright != null) playwright.close();
        } catch (Exception e) {
            log.warn("[pw] playwright close failed: {}", e.toString());
        }
        log.info("[pw] stopped chromium");
    }
}
