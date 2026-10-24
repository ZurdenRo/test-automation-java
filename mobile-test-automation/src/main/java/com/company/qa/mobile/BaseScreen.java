package com.company.qa.mobile;

import io.appium.java_client.AppiumDriver;

import java.util.Objects;
import java.util.function.Function;

/**
 * Base of a consumer's screen: one screen of the app under test, exposing what a user can do there. The driver is
 * protected so that tests call {@code login.loginAs(user)}, not Appium. A screen that needs something only
 * Android offers extends {@code AndroidScreen} instead. Its constructor takes the driver and calls
 * {@code super(driver)}.
 */
public abstract class BaseScreen {

    private final AppiumDriver driver;

    protected BaseScreen(AppiumDriver driver) {
        this.driver = Objects.requireNonNull(driver, "driver");
    }

    /** The session's driver; it always acts on the device the test runs on. */
    protected AppiumDriver driver() {
        return driver;
    }

    /**
     * Waits, polling, until the condition returns something truthy (not null or false) and returns it; an element
     * not found yet counts as "not yet". Fails with Selenium's {@code TimeoutException} after
     * {@code wait.until.timeout.ms}. The implicit wait is 0, so this is the only waiting there is:
     * {@code waitUntil(d -> d.findElement(AppiumBy.accessibilityId("Save")).isDisplayed())}.
     */
    protected <T> T waitUntil(Function<? super AppiumDriver, T> condition) {
        return Waits.until(driver, d -> condition.apply(driver));
    }
}
