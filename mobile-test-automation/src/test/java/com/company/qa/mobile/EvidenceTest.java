package com.company.qa.mobile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceTest {

    @TempDir
    Path classpathDir;

    @TempDir
    Path evidenceDir;

    private FakeAppium appium;

    @BeforeEach
    void startServer() {
        appium = new FakeAppium();
    }

    @AfterEach
    void stopServer() {
        appium.close();
    }

    @Test
    void aFailedTestKeepsAScreenshotThePageSourceAndTheDeviceLog() throws IOException {
        ConsumerRun.Results results = run().run(FailsTest.class);

        assertEquals(List.of("fails"), results.failed());
        Path dir = evidenceDir.resolve(FailsTest.class.getName());
        assertArrayEquals(FakeAppium.SCREENSHOT, Files.readAllBytes(dir.resolve("fails.png")));
        assertEquals(FakeAppium.PAGE_SOURCE, Files.readString(dir.resolve("fails.xml")));
        assertEquals(0, appium.sessionsOpen());
    }

    @Test
    void theDeviceLogKeepsTheLast25AppiumLinesAndTheCrashesOfTheApp() throws IOException {
        run().run(FailsTest.class);

        String log = Files.readString(evidenceDir.resolve(FailsTest.class.getName()).resolve("fails-logcat.txt"));
        String deviceLog = log.substring(log.indexOf("== Device log"));
        assertTrue(deviceLog.startsWith("== Device log (last 25 appium and crash lines) ==\n"), deviceLog);
        assertTrue(deviceLog.contains("appium line 30\n"), deviceLog);
        assertTrue(deviceLog.contains("appium line 7\n"), deviceLog);
        assertFalse(deviceLog.contains("appium line 6\n"), deviceLog);
        assertTrue(deviceLog.contains("FATAL EXCEPTION: main"), deviceLog);
        assertFalse(deviceLog.contains("WindowManager"), deviceLog);
        assertFalse(deviceLog.contains("OtherTag"), deviceLog);
    }

    @Test
    void theFailureIsSummarisedWithoutSeleniumsBoilerplate() throws IOException {
        run().run(SeleniumFailsTest.class);

        String log = Files.readString(evidenceDir.resolve(SeleniumFailsTest.class.getName())
                .resolve("fails-logcat.txt"));
        String failure = log.substring(0, log.indexOf("== Device log"));
        assertTrue(failure.startsWith("== Failure ==\nNoSuchElementException: An element could not be located"),
                failure);
        assertTrue(failure.contains("  caused by IllegalStateException: root cause\n"), failure);
        assertTrue(failure.contains("\n  at "), failure);
        assertFalse(failure.contains("Build info"), failure);
        assertFalse(failure.contains("For documentation"), failure);
    }

    @Test
    void theDeviceLogIsReadBeforeTheScreenshotAddsItsOwnLines() throws IOException {
        run().run(FailsTest.class);

        List<String> requests = appium.requests();
        int log = indexOfEnding(requests, "/se/log");
        assertTrue(log >= 0 && log < indexOfEnding(requests, "/screenshot"), requests::toString);
        assertTrue(log < indexOfEnding(requests, "/source"), requests::toString);
    }

    @Test
    void theFailureIsKeptEvenIfTheDeviceLogCannotBeRead() throws IOException {
        appium.refuseLog(true);

        run().run(SeleniumFailsTest.class);

        String log = Files.readString(evidenceDir.resolve(SeleniumFailsTest.class.getName())
                .resolve("fails-logcat.txt"));
        assertTrue(log.startsWith("== Failure ==\nNoSuchElementException"), log);
        assertTrue(log.contains("(could not read the device log: "), log);
    }

    private static int indexOfEnding(List<String> requests, String suffix) {
        for (int i = 0; i < requests.size(); i++) {
            if (requests.get(i).endsWith(suffix)) {
                return i;
            }
        }
        return -1;
    }

    // JDK 11's HttpClient trips its own assert reading a large body when assertions are on (-ea), as Gradle and
    // Surefire run tests by default; a real screenshot or UI tree is that large. The reason for Java 17 (ADR-0005).
    @Test
    void aLargeUiTreeIsKeptWithAssertionsOn() throws IOException {
        String large = "<hierarchy>" + "<node text=\"row\"/>".repeat(200_000) + "</hierarchy>";
        appium.pageSource(large);

        run().run(FailsTest.class);

        assertEquals(large, Files.readString(evidenceDir.resolve(FailsTest.class.getName()).resolve("fails.xml")));
    }

    @Test
    void aPassingTestKeepsNothing() throws IOException {
        run().run(PassesTest.class).assertAllPassed();

        assertEquals(List.of(), files());
    }

    @Test
    void aSecondFailureOfTheSameMethodDoesNotOverwriteTheFirst() throws IOException {
        ConsumerRun run = run();
        run.run(FailsTest.class);
        run.run(FailsTest.class);

        assertEquals(List.of("fails-2-logcat.txt", "fails-2.png", "fails-2.xml", "fails-logcat.txt", "fails.png",
                "fails.xml"), files());
    }

    @Test
    void evidenceThatCannotBeTakenNeverHidesTheFailureNorLeaksTheSession() throws IOException {
        appium.refuseEvidence(true);

        ConsumerRun.Results results = run().run(FailsTest.class);

        assertEquals(List.of("fails"), results.failed());
        assertTrue(results.failure("fails") instanceof AssertionError, results::summary);
        // Only the failure summary survives: it needs neither the server nor the device.
        assertEquals(List.of("fails-logcat.txt"), files());
        assertEquals(0, appium.sessionsOpen());
    }

    private ConsumerRun run() {
        return ConsumerRun.in(classpathDir)
                .config("appium-server-url", appium.url())
                .config("android.app", "/apps/ApiDemos-debug.apk")
                .config("android.devices", "emulator-5554")
                .config("evidence-dir", evidenceDir.toString());
    }

    private List<String> files() throws IOException {
        try (Stream<Path> all = Files.walk(evidenceDir)) {
            return all.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).sorted()
                    .collect(Collectors.toList());
        }
    }

    public static class FailsTest extends BaseMobileTest {
        @org.testng.annotations.Test
        public void fails() {
            throw new AssertionError("the screen is not the one expected");
        }
    }

    public static class SeleniumFailsTest extends BaseMobileTest {
        @org.testng.annotations.Test
        public void fails() {
            throw new org.openqa.selenium.NoSuchElementException("An element could not be located on the page\n"
                    + "For documentation on this error, please visit: https://example.org\n"
                    + "Build info: version: '4.50.0'\nSystem info: os.name: 'Linux'",
                    new IllegalStateException("root cause"));
        }
    }

    public static class PassesTest extends BaseMobileTest {
        @org.testng.annotations.Test
        public void passes() {
        }
    }
}
