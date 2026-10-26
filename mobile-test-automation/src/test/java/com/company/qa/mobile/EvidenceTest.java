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
        assertEquals(String.join("\n", FakeAppium.LOGCAT) + "\n", Files.readString(dir.resolve("fails-logcat.txt")));
        assertEquals(0, appium.sessionsOpen());
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
        assertEquals(List.of(), files());
        assertEquals(0, appium.sessionsOpen());
    }

    private ConsumerRun run() {
        return ConsumerRun.in(classpathDir)
                .config("appium.server.url", appium.url())
                .config("android.app", "/apps/ApiDemos-debug.apk")
                .config("android.devices", "emulator-5554")
                .config("evidence.dir", evidenceDir.toString());
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

    public static class PassesTest extends BaseMobileTest {
        @org.testng.annotations.Test
        public void passes() {
        }
    }
}
