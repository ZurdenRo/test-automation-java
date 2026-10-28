package com.company.qa.mobile;

import com.company.qa.mobile.android.AndroidScreen;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The library against a real Appium server and an emulator, with Appium's own ApiDemos app. */
class DeviceTest {

    // The server downloads it, so the run needs no local file; the version is pinned (Apache-2.0).
    private static final String APP =
            "https://github.com/appium/android-apidemos/releases/download/v6.0.18/ApiDemos-debug.apk";
    private static final String APP_PACKAGE = "io.appium.android.apis";

    @TempDir
    Path classpathDir;

    @TempDir
    Path evidenceDir;

    @Test
    void eachTestStartsTheAppFromItsFirstScreenOnTheDevice() {
        ConsumerRun.Results results = run().run(NavigatesTest.class);

        results.assertAllPassed();
        assertEquals(2, results.passed().size());
    }

    @Test
    void anAppAlreadyInstalledIsOpenedByItsPackage() {
        // Installs ApiDemos from the APK first, so this test does not depend on another having run.
        run().run(NavigatesTest.class).assertAllPassed();

        ConsumerRun.Results results = ConsumerRun.in(classpathDir)
                .config("appium-server-url", "http://localhost:4723")
                .config("android.app-package", APP_PACKAGE)
                .config("android.devices", "emulator-5554")
                .config("evidence-dir", evidenceDir.toString())
                .run(NavigatesTest.class);

        results.assertAllPassed();
        assertEquals(2, results.passed().size());
    }

    @Test
    void aFailedTestKeepsRealEvidence() throws IOException {
        ConsumerRun.Results results = run().run(FailsTest.class);

        assertEquals(List.of("fails"), results.failed());
        Path dir = evidenceDir.resolve(FailsTest.class.getName());
        assertTrue(Files.size(dir.resolve("fails.png")) > 1_000, "screenshot");
        assertTrue(Files.readString(dir.resolve("fails.xml")).contains("<hierarchy"), "UI tree");
        assertTrue(Files.exists(dir.resolve("fails-logcat.txt")), files(dir)::toString);
    }

    private ConsumerRun run() {
        return ConsumerRun.in(classpathDir)
                .config("appium-server-url", "http://localhost:4723")
                .config("android.app", APP)
                .config("android.devices", "emulator-5554")
                .config("evidence-dir", evidenceDir.toString());
    }

    private static List<String> files(Path dir) throws IOException {
        try (Stream<Path> all = Files.list(dir)) {
            return all.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
        }
    }

    public static class MainScreen extends AndroidScreen {
        public MainScreen(AndroidDriver driver) {
            super(driver);
        }

        MainScreen waitUntilShown() {
            waitUntil(d -> d.findElement(AppiumBy.accessibilityId("Accessibility")).isDisplayed());
            return this;
        }

        String activity() {
            return driver().currentActivity();
        }

        ViewsScreen openViews() {
            driver().findElement(AppiumBy.accessibilityId("Views")).click();
            return new ViewsScreen(driver());
        }
    }

    public static class ViewsScreen extends BaseScreen {
        public ViewsScreen(AppiumDriver driver) {
            super(driver);
        }

        boolean showsAnimation() {
            return waitUntil(d -> d.findElement(AppiumBy.accessibilityId("Animation")).isDisplayed());
        }
    }

    public static class NavigatesTest extends BaseMobileTest {
        @org.testng.annotations.Test(priority = 1)
        public void opensViews() {
            MainScreen main = on(MainScreen.class).waitUntilShown();
            if (!main.activity().endsWith("ApiDemos")) {
                throw new AssertionError("activity " + main.activity());
            }
            if (!main.openViews().showsAnimation()) {
                throw new AssertionError("Views did not open");
            }
        }

        // Runs after opensViews on the same device: the app starts over from its first screen, not from Views.
        @org.testng.annotations.Test(priority = 2)
        public void startsOverFromTheFirstScreen() {
            on(MainScreen.class).waitUntilShown();
        }
    }

    public static class FailsTest extends BaseMobileTest {
        @org.testng.annotations.Test
        public void fails() {
            on(MainScreen.class).waitUntilShown();
            throw new AssertionError("the test failed with the app on screen");
        }
    }
}
