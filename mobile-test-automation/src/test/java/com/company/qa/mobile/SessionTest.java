package com.company.qa.mobile;

import com.company.qa.mobile.android.AndroidScreen;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTest {

    @TempDir
    Path classpathDir;

    private final ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
    private FakeAppium appium;

    @BeforeEach
    void startServer() {
        appium = new FakeAppium();
    }

    @AfterEach
    void stopServer() {
        Thread.currentThread().setContextClassLoader(originalLoader);
        appium.close();
    }

    @Test
    void eachTestMethodGetsItsOwnSessionOnTheDeviceWithTheAppAndClosesIt() {
        run().run(TwoTestsTest.class).assertAllPassed();

        assertEquals(2, appium.sessionsCreated());
        assertEquals(0, appium.sessionsOpen());
        for (String capabilities : appium.sessionRequests()) {
            assertTrue(capabilities.contains("\"appium:udid\":\"emulator-5554\""), capabilities);
            assertTrue(capabilities.contains("\"appium:app\":\"/apps/ApiDemos-debug.apk\""), capabilities);
            assertTrue(capabilities.contains("\"appium:noReset\":false"), capabilities);
            assertTrue(capabilities.contains("\"appium:automationName\":\"UIAutomator2\""), capabilities);
        }
    }

    @Test
    void anAppAlreadyInstalledIsOpenedByItsPackage() {
        ConsumerRun run = ConsumerRun.in(classpathDir)
                .config("appium.server.url", appium.url())
                .config("android.app.package", "com.jeffprod.todo")
                .config("android.devices", "emulator-5554");

        run.run(TwoTestsTest.class).assertAllPassed();

        String capabilities = appium.sessionRequests().get(0);
        assertTrue(capabilities.contains("\"appium:appPackage\":\"com.jeffprod.todo\""), capabilities);
        assertTrue(!capabilities.contains("appium:app\""), capabilities);
    }

    @Test
    void theImplicitWaitIsSetToZero() {
        run().run(TwoTestsTest.class).assertAllPassed();

        assertTrue(appium.requests().stream().anyMatch(r -> r.matches("POST /session/[^/]+/timeouts")),
                appium.requests()::toString);
    }

    @Test
    void parallelTestsTakeOneDeviceEachAndNeverShareOne() {
        run("emulator-5554,emulator-5556").parallelMethods().run(SlowTest.class).assertAllPassed();

        assertEquals(2, appium.maxSessionsOpen());
        assertEquals(0, appium.sessionsOpen());
        assertTrue(appium.sessionRequests().stream().anyMatch(c -> c.contains("emulator-5554")));
        assertTrue(appium.sessionRequests().stream().anyMatch(c -> c.contains("emulator-5556")));
    }

    @Test
    void withOneDeviceParallelTestsRunOneAfterAnother() {
        ConsumerRun.Results results = run().parallelMethods().run(SlowTest.class).assertAllPassed();

        assertEquals(4, results.passed().size());
        assertEquals(1, appium.maxSessionsOpen());
    }

    @Test
    void aSessionThatCannotStartSaysWhichDeviceAndServerAndFreesTheDevice() throws IOException {
        Files.writeString(classpathDir.resolve("mobile-test-qa.properties"), "appium.server.url=" + appium.url()
                + "\nandroid.app=/apps/ApiDemos-debug.apk\nandroid.devices=emulator-5554\n"
                + "device.wait.timeout.ms=1000\n");
        Thread.currentThread().setContextClassLoader(
                new URLClassLoader(new URL[]{classpathDir.toUri().toURL()}, originalLoader));
        MobileConfig config = MobileConfigLoader.load(Map.of());
        appium.refuseSessions(true);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> MobileSession.start(config));

        assertTrue(e.getMessage().startsWith("Could not start a session on device 'emulator-5554' at Appium server "
                + appium.url() + ": is the server up"), e.getMessage());
        assertTrue(e.getMessage().endsWith("Device emulator-9999 is not connected"), e.getMessage());
        // The only device went back to the pool: starting again takes it at once instead of timing out.
        appium.refuseSessions(false);
        MobileSession.start(config).close();
        assertEquals(1, appium.sessionsCreated());
        assertEquals(0, appium.sessionsOpen());
    }

    @Test
    void aTestGetsItsScreensAndItsEnvironment() {
        run().run(UsesScreensTest.class).assertAllPassed();
    }

    @Test
    void waitUntilFailsWithATimeoutAfterTheConfiguredTime() {
        long start = System.nanoTime();

        ConsumerRun.Results results = run().config("wait.until.timeout.ms", "700").run(WaitsTest.class);

        assertTrue(results.failure("waitsForWhatNeverComes") instanceof TimeoutException,
                results::summary);
        assertTrue((System.nanoTime() - start) / 1_000_000 < 8_000);
    }

    @Test
    void aMissingFileForTheEnvironmentFailsTheRunBeforeAnySession() {
        ConsumerRun.Results results = ConsumerRun.in(classpathDir)
                .suiteParameter("env", "prod")
                .run(TwoTestsTest.class);

        assertTrue(results.summary().contains("mobile-test-prod.properties"), results::summary);
        assertEquals(0, appium.sessionsCreated());
    }

    private ConsumerRun run() {
        return run("emulator-5554");
    }

    private ConsumerRun run(String devices) {
        return ConsumerRun.in(classpathDir)
                .config("appium.server.url", appium.url())
                .config("android.app", "/apps/ApiDemos-debug.apk")
                .config("android.devices", devices);
    }

    public static class TwoTestsTest extends BaseMobileTest {
        @org.testng.annotations.Test
        public void first() {
        }

        @org.testng.annotations.Test
        public void second() {
        }
    }

    public static class SlowTest extends BaseMobileTest {
        @org.testng.annotations.Test
        public void a() throws InterruptedException {
            Thread.sleep(300);
        }

        @org.testng.annotations.Test
        public void b() throws InterruptedException {
            Thread.sleep(300);
        }

        @org.testng.annotations.Test
        public void c() throws InterruptedException {
            Thread.sleep(300);
        }

        @org.testng.annotations.Test
        public void d() throws InterruptedException {
            Thread.sleep(300);
        }
    }

    public static class PlainScreen extends BaseScreen {
        public PlainScreen(AppiumDriver driver) {
            super(driver);
        }

        AppiumDriver theDriver() {
            return driver();
        }
    }

    public static class AndroidOnlyScreen extends AndroidScreen {
        public AndroidOnlyScreen(AndroidDriver driver) {
            super(driver);
        }

        AndroidDriver theDriver() {
            return driver();
        }
    }

    public static class UsesScreensTest extends BaseMobileTest {
        @org.testng.annotations.Test
        public void usesThem() {
            if (!environment().equals("qa")) {
                throw new AssertionError(environment());
            }
            if (on(PlainScreen.class).theDriver() == null || on(AndroidOnlyScreen.class).theDriver() == null) {
                throw new AssertionError("no driver");
            }
        }
    }

    public static class WaitsScreen extends BaseScreen {
        public WaitsScreen(AppiumDriver driver) {
            super(driver);
        }

        WebElement waitForSave() {
            return waitUntil(d -> d.findElement(io.appium.java_client.AppiumBy.accessibilityId("Save")));
        }
    }

    public static class WaitsTest extends BaseMobileTest {
        @org.testng.annotations.Test
        public void waitsForWhatNeverComes() {
            on(WaitsScreen.class).waitForSave();
        }
    }
}
