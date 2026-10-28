package com.company.qa.mobile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.Reporter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Base of a consumer's mobile test. Before each test method it opens a fresh Appium session on a free device;
 * after it, it closes the session and returns the device. The test gets screens from {@code on(...)} and never
 * touches the driver.
 */
public abstract class BaseMobileTest {

    private static final Logger LOG = LoggerFactory.getLogger(BaseMobileTest.class);

    // TestNG shares one instance across threads when it runs methods in parallel.
    private final ThreadLocal<MobileSession> session = new ThreadLocal<>();

    /** Logs, once per {@code <test>}, the environment and platform it runs on. */
    @BeforeTest(alwaysRun = true)
    public void logEnvironment(ITestContext context) {
        MobileConfigLoader.logEnvironment(context.getName(), context.getCurrentXmlTest().getAllParameters());
    }

    @BeforeMethod(alwaysRun = true)
    public void startSession(ITestContext context) {
        Map<String, String> xmlParameters = context.getCurrentXmlTest().getAllParameters();
        session.set(MobileSession.start(MobileConfigLoader.load(xmlParameters)));
    }

    /** Keeps the failure evidence of a failed test (logged to the TestNG report), then closes the session. */
    @AfterMethod(alwaysRun = true)
    public void closeSession(ITestResult result) {
        MobileSession current = session.get();
        if (current != null) {
            session.remove();
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

    /**
     * The suite's environment: {@code qa} unless {@code <parameter name="env">} names another. Its settings come from
     * {@code test-automation-<env>.yaml}; a service client names its environment the same way, such as
     * {@code new UsersClient(environment())}, so one parameter moves the whole test.
     */
    protected String environment() {
        return session.get().environment();
    }

    /**
     * A secret (a user, a password, a key) by name: {@code secret("admin-password")} reads the environment variable
     * that {@code mobile.secrets.admin-password} names in the environment's config file, so the test is the same in every
     * environment. Pass it to the screen; never log it.
     */
    protected String secret(String name) {
        return session.get().secret(name);
    }

    /**
     * The screen the app shows now: the app is launched when the session starts, so this is how a test begins, and
     * how it picks up the screen an action reached.
     */
    protected <T extends BaseScreen> T on(Class<T> type) {
        return session.get().on(type);
    }
}
