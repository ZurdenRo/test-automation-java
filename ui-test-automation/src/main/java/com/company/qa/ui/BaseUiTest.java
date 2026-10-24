package com.company.qa.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.Reporter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Base of a consumer's UI test. Before each test method it opens a fresh browser context; after it, it keeps
 * failure evidence if the test failed and closes the context. The test gets page objects from {@code open(...)},
 * {@code to(...)}, {@code openInNewTab(...)} and {@code on(...)}, and never touches the browser page.
 */
public abstract class BaseUiTest {

    private static final Logger LOG = LoggerFactory.getLogger(BaseUiTest.class);

    // TestNG shares one instance across threads when it runs methods in parallel.
    private final ThreadLocal<UiContext> uiContext = new ThreadLocal<>();

    /** Logs, once per {@code <test>}, the environment it runs on. */
    @BeforeTest(alwaysRun = true)
    public void logEnvironment(ITestContext context) {
        UiConfigLoader.logEnvironment(context.getName(), context.getCurrentXmlTest().getAllParameters());
    }

    @BeforeMethod(alwaysRun = true)
    public void startBrowserContext(ITestContext context) {
        Map<String, String> xmlParameters = context.getCurrentXmlTest().getAllParameters();
        uiContext.set(UiContext.start(UiConfigLoader.load(xmlParameters)));
    }

    /** Keeps the failure evidence of a failed test (logged to the TestNG report), then closes the context. */
    @AfterMethod(alwaysRun = true)
    public void closeBrowserContext(ITestResult result) {
        UiContext current = uiContext.get();
        if (current != null) {
            uiContext.remove();
            boolean failed = result.getStatus() == ITestResult.FAILURE;
            List<Path> evidence = current.close(result.getTestClass().getName(), result.getMethod().getMethodName(),
                    failed);
            for (Path file : evidence) {
                Reporter.log("Failure evidence: " + file.toAbsolutePath());
                LOG.info("Failure evidence of {}.{}: {}", result.getTestClass().getName(),
                        result.getMethod().getMethodName(), file.toAbsolutePath());
            }
        }
    }

    @AfterSuite(alwaysRun = true)
    public void closeBrowsers() {
        Browsers.closeAll();
    }

    /**
     * The suite's environment: {@code qa} unless {@code <parameter name="env">} names another. Its settings come from
     * {@code ui-test-<env>.properties}; a service client can name its own file the same way, such as
     * {@code new UsersClient(environment())} reading {@code users-service-<env>.properties}, so one parameter moves
     * the whole test.
     */
    protected String environment() {
        return uiContext.get().environment();
    }

    /**
     * A secret (a user, a password, a key) by name: {@code secret("admin.password")} reads the environment variable
     * that {@code secret.admin.password} names in the environment's config file, so the test is the same in every
     * environment. Pass it to the page object; never log it.
     */
    protected String secret(String name) {
        return uiContext.get().secret(name);
    }

    /** Navigates the test's tab to the page object's path and returns the page object. How a test starts. */
    protected <T extends BasePage> T open(Class<T> type) {
        return to(type).open();
    }

    /** Opens the page object's path in a new tab; the page object returned acts on that tab. */
    protected <T extends BasePage> T openInNewTab(Class<T> type) {
        return to(type).openInNewTab();
    }

    /** For a page object whose path has named parameters: {@code to(OrderPage.class).with("id", 7).open()}. */
    protected <T extends BasePage> PageNavigation<T> to(Class<T> type) {
        return new PageNavigation<>(type, uiContext.get());
    }

    /**
     * The page object for the screen the test's tab shows now, without navigating: for a screen the app reached
     * on its own, after a redirect or a click whose destination the previous page object cannot know.
     */
    protected <T extends BasePage> T on(Class<T> type) {
        return uiContext.get().on(type);
    }
}
