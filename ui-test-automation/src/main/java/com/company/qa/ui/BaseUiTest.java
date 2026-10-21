package com.company.qa.ui;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;

/**
 * Base of a consumer's UI test. Before each test method it opens a fresh browser context and closes it after;
 * the test gets page objects from {@code open(...)} and never touches the browser page.
 */
public abstract class BaseUiTest {

    private static final String DEFAULT_CONFIG_RESOURCE = "ui-test.properties";

    // TestNG shares one instance across threads when it runs methods in parallel.
    private final ThreadLocal<UiSession> session = new ThreadLocal<>();

    @BeforeMethod(alwaysRun = true)
    public void startBrowserContext() {
        session.set(UiSession.start(UiConfigLoader.load(DEFAULT_CONFIG_RESOURCE, System.getenv())));
    }

    @AfterMethod(alwaysRun = true)
    public void closeBrowserContext() {
        UiSession current = session.get();
        if (current != null) {
            session.remove();
            current.close();
        }
    }

    @AfterSuite(alwaysRun = true)
    public void closeBrowsers() {
        UiSession.closeBrowsers();
    }

    /** Navigates the test's tab to the page object's path and returns the page object. */
    protected <T extends BasePage> T open(Class<T> type) {
        return session.get().open(type);
    }
}
