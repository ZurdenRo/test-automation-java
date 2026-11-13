package com.company.qa.ui;

import com.company.qa.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.Reporter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
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
    private volatile String environment;

    /** Logs, once per {@code <test>}, the environment it runs on. */
    @BeforeTest(alwaysRun = true)
    public void logEnvironment(ITestContext context) {
        UiConfigLoader.logEnvironment(context.getName(), context.getCurrentXmlTest().getAllParameters());
    }

    /** Reads the suite's environment once per test class, so that a {@code @BeforeClass} method can use it. */
    @BeforeClass(alwaysRun = true)
    public void readEnvironment(ITestContext context) {
        environment = Config.environmentFrom(context.getCurrentXmlTest().getAllParameters());
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
     * {@code test-automation-<env>.yaml}. Pass it to the service clients of the test, so that one parameter moves the
     * whole test: {@code new UsersClient(environment())}. Available from a {@code @BeforeClass} method on, where a
     * client is best created (one per class); a field initializer or a constructor of the test class runs before.
     */
    protected String environment() {
        if (environment == null) {
            throw new IllegalStateException("environment() is available from a @BeforeClass method on: TestNG reads"
                    + " the suite's parameters after it builds the test class, so not in a field initializer or a"
                    + " constructor");
        }
        return environment;
    }

    /**
     * A secret of an app (a user, a password, a key) by name: {@code secret("users", "password")} reads the
     * environment variable that {@code ui.apps.users.secrets.password} names in the environment's config file, so the
     * test is the same in every environment. A page object reads its own app's with {@code secret(name)}. Never log
     * it.
     */
    protected String secret(String app, String name) {
        return uiContext.get().secret(app, name);
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
