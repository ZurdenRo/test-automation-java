package com.company.qa.mobile;

import io.appium.java_client.support.ui.FluentWait;
import org.openqa.selenium.NotFoundException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * The explicit waits of screens and components (ADR-0002). A session records its driver's timeout here, so a screen,
 * which only holds the driver, waits as long as the config says.
 */
final class Waits {

    private static final Duration DEFAULT = Duration.ofSeconds(10);
    private static final Duration POLLING = Duration.ofMillis(500);
    private static final Map<WebDriver, Duration> TIMEOUTS = Collections.synchronizedMap(new WeakHashMap<>());

    private Waits() {
    }

    static void register(WebDriver driver, int timeoutMs) {
        TIMEOUTS.put(driver, Duration.ofMillis(timeoutMs));
    }

    static void unregister(WebDriver driver) {
        TIMEOUTS.remove(driver);
    }

    /**
     * Polls the condition until it returns something truthy (not null or false). An element not found yet, or one
     * that went stale between polls, counts as "not yet".
     *
     * @return what the condition returned, or throws Selenium's {@code TimeoutException}
     */
    static <T> T until(WebDriver driver, Function<? super WebDriver, T> condition) {
        return new FluentWait<>(driver)
                .withTimeout(TIMEOUTS.getOrDefault(driver, DEFAULT))
                .pollingEvery(POLLING)
                .ignoring(NotFoundException.class, StaleElementReferenceException.class)
                .until(condition);
    }
}
