package com.company.qa.mobile;

import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.WebElement;

import java.util.Objects;
import java.util.function.Function;

/**
 * Base of a consumer's component: a part of a screen that repeats or stands on its own (a toolbar, a list row, a
 * dialog). Screens create it from the driver and the element of its root; it looks for everything within that root.
 */
public abstract class BaseComponent {

    private final AppiumDriver driver;
    private final WebElement root;

    protected BaseComponent(AppiumDriver driver, WebElement root) {
        this.driver = Objects.requireNonNull(driver, "driver");
        this.root = Objects.requireNonNull(root, "root");
    }

    /** The component's root element; look within it ({@code root().findElement(...)}). */
    protected WebElement root() {
        return root;
    }

    /**
     * Waits, polling, until the condition, given the root, returns something truthy and returns it; the same wait
     * as a screen's {@code waitUntil}. Selenium's {@code ExpectedConditions} take the driver, not the root, so here
     * the condition is a lambda: {@code waitUntil(root -> root.findElement(TITLE).getText().equals("Done"))}.
     */
    protected <T> T waitUntil(Function<? super WebElement, T> condition) {
        return Waits.until(driver, d -> condition.apply(root));
    }
}
