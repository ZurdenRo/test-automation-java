package com.company.qa.ui;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The physical browsers, knowing no test runner. Each thread launches its browser once and reuses it for every
 * test it runs; the browser contexts are the tests' own.
 */
final class Browsers {

    private static final Logger LOG = LoggerFactory.getLogger(Browsers.class);
    private static final ThreadLocal<Launched> THREAD_BROWSERS = new ThreadLocal<>();
    private static final List<Playwright> ALL_PLAYWRIGHTS = new CopyOnWriteArrayList<>();
    // Bumped when browsers are closed, so a thread does not reuse one closed by an earlier suite in the same JVM.
    private static final AtomicInteger GENERATION = new AtomicInteger();

    private Browsers() {
    }

    /** The thread's browser for these launch settings; a run whose {@code <test>}s vary them has one per kind. */
    static Browser forThread(UiConfig config) {
        Launched launched = THREAD_BROWSERS.get();
        if (launched == null || launched.generation != GENERATION.get()) {
            Playwright playwright = Playwright.create();
            ALL_PLAYWRIGHTS.add(playwright);
            launched = new Launched(GENERATION.get(), playwright);
            THREAD_BROWSERS.set(launched);
        }
        String kind = config.browser() + "/" + config.channel() + "/" + config.headless() + "/" + config.slowMoMs();
        Playwright playwright = launched.playwright;
        return launched.browsers.computeIfAbsent(kind, k -> {
            LOG.info("Launching {}{} ({}) on thread {}", config.browser(),
                    config.channel() == null ? "" : " channel " + config.channel(),
                    config.headless() ? "headless" : "headed", Thread.currentThread().getName());
            return type(playwright, config.browser()).launch(new BrowserType.LaunchOptions()
                    .setChannel(config.channel())
                    .setHeadless(config.headless())
                    .setSlowMo(config.slowMoMs()));
        });
    }

    /** Closes every browser launched by any thread. */
    static void closeAll() {
        GENERATION.incrementAndGet();
        for (Playwright playwright : ALL_PLAYWRIGHTS) {
            playwright.close();
        }
        ALL_PLAYWRIGHTS.clear();
    }

    private static BrowserType type(Playwright playwright, String browser) {
        switch (browser) {
            case "firefox":
                return playwright.firefox();
            case "webkit":
                return playwright.webkit();
            default:
                return playwright.chromium();
        }
    }

    private static final class Launched {
        final int generation;
        final Playwright playwright;
        final Map<String, Browser> browsers = new HashMap<>();

        Launched(int generation, Playwright playwright) {
            this.generation = generation;
            this.playwright = playwright;
        }
    }
}
