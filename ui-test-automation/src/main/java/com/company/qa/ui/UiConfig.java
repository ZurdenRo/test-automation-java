package com.company.qa.ui;

import java.nio.file.Path;

/** The settings of one test method's browser context, resolved from every source. */
final class UiConfig {

    private final UiConfigLoader loader;
    private final String browser;
    private final String channel;
    private final boolean headless;
    private final int timeoutMs;
    private final int viewportWidth;
    private final int viewportHeight;
    private final int slowMoMs;
    private final Path evidenceDir;
    private final boolean traceOnFailure;

    UiConfig(UiConfigLoader loader, String browser, String channel, boolean headless, int timeoutMs,
             int viewportWidth, int viewportHeight, int slowMoMs, Path evidenceDir, boolean traceOnFailure) {
        this.loader = loader;
        this.browser = browser;
        this.channel = channel;
        this.headless = headless;
        this.timeoutMs = timeoutMs;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        this.slowMoMs = slowMoMs;
        this.evidenceDir = evidenceDir;
        this.traceOnFailure = traceOnFailure;
    }

    /** The named app's base URL, resolved when a page needs it: a run that never opens its pages needs none. */
    String appUrl(String app, String pageName) {
        return loader.appUrl(app, pageName);
    }

    /** chromium, firefox or webkit. */
    String browser() {
        return browser;
    }

    /** chrome or msedge, the browser installed on the machine instead of Playwright's; null for none. */
    String channel() {
        return channel;
    }

    boolean headless() {
        return headless;
    }

    /** How long Playwright waits for an action before failing it. */
    int timeoutMs() {
        return timeoutMs;
    }

    int viewportWidth() {
        return viewportWidth;
    }

    int viewportHeight() {
        return viewportHeight;
    }

    /** A pause before each Playwright action, to watch a run; 0 for none. */
    int slowMoMs() {
        return slowMoMs;
    }

    Path evidenceDir() {
        return evidenceDir;
    }

    boolean traceOnFailure() {
        return traceOnFailure;
    }
}
