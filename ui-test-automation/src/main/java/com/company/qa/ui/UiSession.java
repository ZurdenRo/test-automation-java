package com.company.qa.ui;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Tracing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One test method's browser context, knowing no test runner. Each thread launches its browser once and reuses
 * it; each session gets a fresh context, so nothing one test leaves behind reaches another.
 */
final class UiSession {

    private static final Logger LOG = LoggerFactory.getLogger(UiSession.class);
    private static final ThreadLocal<Launched> BROWSER = new ThreadLocal<>();
    private static final List<Playwright> LAUNCHED = new CopyOnWriteArrayList<>();
    // Bumped when browsers are closed, so a thread does not reuse one closed by an earlier suite in the same JVM.
    private static final AtomicInteger GENERATION = new AtomicInteger();

    private final UiConfig config;
    private final BrowserContext context;
    private final Page page;

    private UiSession(UiConfig config, BrowserContext context) {
        this.config = config;
        this.context = context;
        this.page = context.newPage();
    }

    static UiSession start(UiConfig config) {
        BrowserContext context = browser(config).newContext(new Browser.NewContextOptions()
                .setViewportSize(config.viewportWidth(), config.viewportHeight()));
        context.setDefaultTimeout(config.timeoutMs());
        if (config.traceOnFailure()) {
            context.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true));
        }
        return new UiSession(config, context);
    }

    /** Closes every browser launched by any thread. */
    static void closeBrowsers() {
        GENERATION.incrementAndGet();
        for (Playwright playwright : LAUNCHED) {
            playwright.close();
        }
        LAUNCHED.clear();
    }

    /**
     * @param parameters the values of the path's named parameters, all of them or none if it has none
     * @param newTab     open it in a new tab of the context instead of the test's tab
     */
    <T extends BasePage> T open(Class<T> type, Map<String, String> parameters, boolean newTab) {
        Page tab = newTab ? context.newPage() : page;
        T pageObject = create(type, tab);
        String name = type.getSimpleName();
        String template = pageObject.path();
        if (template == null) {
            throw new IllegalStateException(name + " has no path: reach it from another page, or use on("
                    + name + ".class)");
        }
        String app = pageObject.app();
        if (app == null) {
            throw new IllegalStateException(name + " has a path but no app: override app() to name the app whose"
                    + " app.<name>.url it lives under");
        }
        String url = PagePath.url(name, config.appUrl(app, name), template, parameters);
        LOG.debug("Opening {} at {}{}", name, url, newTab ? " in a new tab" : "");
        tab.navigate(url);
        return pageObject;
    }

    String environment() {
        return config.environment();
    }

    String secret(String name) {
        return config.secret(name);
    }

    /** The page object for the screen the test's tab shows now, without navigating. */
    <T extends BasePage> T on(Class<T> type) {
        return create(type, page);
    }

    /**
     * Keeps the failure evidence of a failed test, then closes the context. Evidence that cannot be taken (a
     * crashed tab) is skipped, so it never hides why the test failed.
     *
     * @param testClass  names the evidence directory
     * @param testMethod names the evidence files; a second failure of the same method gets {@code -2}
     * @return the evidence files kept, none for a test that passed
     */
    List<Path> close(String testClass, String testMethod, boolean failed) {
        List<Path> evidence = new ArrayList<>();
        try {
            if (failed) {
                Path stem = unusedStem(config.evidenceDir().resolve(testClass), testMethod);
                List<Page> tabs = context.pages();
                for (int i = 0; i < tabs.size(); i++) {
                    Path file = sibling(stem, i == 0 ? ".png" : "-tab" + (i + 1) + ".png");
                    try {
                        tabs.get(i).screenshot(new Page.ScreenshotOptions().setPath(file));
                        evidence.add(file);
                    } catch (PlaywrightException ignored) {
                        // The tab is gone; the other evidence still helps.
                    }
                }
                if (config.traceOnFailure()) {
                    Path trace = sibling(stem, "-trace.zip");
                    context.tracing().stop(new Tracing.StopOptions().setPath(trace));
                    evidence.add(trace);
                }
            } else if (config.traceOnFailure()) {
                context.tracing().stop();
            }
        } finally {
            context.close();
        }
        return evidence;
    }

    private static Path unusedStem(Path dir, String testMethod) {
        Path stem = dir.resolve(testMethod);
        for (int n = 2; Files.exists(sibling(stem, ".png")); n++) {
            stem = dir.resolve(testMethod + "-" + n);
        }
        return stem;
    }

    private static Path sibling(Path stem, String suffix) {
        return stem.resolveSibling(stem.getFileName() + suffix);
    }

    /** The thread's browser for these launch settings; a run whose {@code <test>}s vary them has one per kind. */
    private static Browser browser(UiConfig config) {
        Launched launched = BROWSER.get();
        if (launched == null || launched.generation != GENERATION.get()) {
            Playwright playwright = Playwright.create();
            LAUNCHED.add(playwright);
            launched = new Launched(GENERATION.get(), playwright);
            BROWSER.set(launched);
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

    private static <T extends BasePage> T create(Class<T> type, Page page) {
        try {
            Constructor<T> constructor = type.getConstructor(Page.class);
            return constructor.newInstance(page);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(type.getSimpleName() + " needs a public constructor taking a Page", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Creating " + type.getSimpleName() + " failed", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Cannot create " + type.getSimpleName(), e);
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
