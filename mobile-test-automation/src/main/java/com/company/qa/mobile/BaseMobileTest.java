package com.company.qa.mobile;

import org.testng.ITestContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;

import java.util.Map;

/**
 * Base of a consumer's mobile test. Before each test method it opens a fresh Appium session on a free device;
 * after it, it closes the session and returns the device. The test gets screens from {@code on(...)} and never
 * touches the driver.
 */
public abstract class BaseMobileTest {

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

    @AfterMethod(alwaysRun = true)
    public void closeSession() {
        MobileSession current = session.get();
        if (current != null) {
            session.remove();
            current.close();
        }
    }

    /**
     * The suite's environment: {@code qa} unless {@code <parameter name="env">} names another. Its settings come from
     * {@code mobile-test-<env>.properties}; a service client can name its own file the same way, such as
     * {@code new UsersClient(environment())} reading {@code users-service-<env>.properties}, so one parameter moves
     * the whole test.
     */
    protected String environment() {
        return session.get().environment();
    }

    /**
     * A secret (a user, a password, a key) by name: {@code secret("admin.password")} reads the environment variable
     * that {@code secret.admin.password} names in the environment's config file, so the test is the same in every
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
