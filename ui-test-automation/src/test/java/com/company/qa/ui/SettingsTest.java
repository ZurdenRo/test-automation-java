package com.company.qa.ui;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testng.ITestContext;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTest {

    private static TestSite site;

    @TempDir
    Path classpathDir;

    @BeforeAll
    static void startSite() {
        site = new TestSite("appA");
    }

    @AfterAll
    static void stopSite() {
        site.close();
    }

    @Test
    void theViewportComesFromTheConfig() {
        ConsumerRun.in(classpathDir).config("apps.shop.url", site.url())
                .config("viewport.width", "800").config("viewport.height", "600")
                .run(ChecksViewportTest.class)
                .assertAllPassed();
    }

    @Test
    void theTimeoutBoundsHowLongAnActionWaits() {
        ConsumerRun.Results results = ConsumerRun.in(classpathDir).config("apps.shop.url", site.url())
                .config("timeout-ms", "300")
                .run(ClicksWhatIsNotThereTest.class);

        assertTrue(results.failure("clicksWhatIsNotThere").getMessage().contains("Timeout 300ms"),
                results.summary());
    }

    @Test
    void eachXmlTestRunsInTheBrowserItsParametersName() {
        ConsumerRun.Results results = ConsumerRun.in(classpathDir).config("apps.shop.url", site.url())
                .xmlTest("on chromium", Map.of("browser", "chromium"))
                .xmlTest("on firefox", Map.of("browser", "firefox"))
                .run(ChecksBrowserTest.class)
                .assertAllPassed();

        assertEquals(List.of("runsInTheNamedBrowser", "runsInTheNamedBrowser"), results.passed());
    }

    @Test
    void theSuiteNamesTheConfigOfItsEnvironment() {
        ConsumerRun.in(classpathDir)
                .resource("test-automation-staging.yaml", ConsumerRun.uiYaml(Map.of("apps.shop.url", site.url(),
                        "viewport.width", "640", "viewport.height", "480")))
                .suiteParameter("env", "staging")
                .run(RunsOnStagingTest.class)
                .assertAllPassed();
    }

    public static class ProbePage extends ShopPage {
        public ProbePage(Page page) {
            super(page);
        }

        @Override
        protected String path() {
            return "/probe";
        }

        public int width() {
            return (Integer) page().evaluate("() => window.innerWidth");
        }

        public int height() {
            return (Integer) page().evaluate("() => window.innerHeight");
        }

        public String userAgent() {
            return (String) page().evaluate("() => navigator.userAgent");
        }

        public void clickMissingButton() {
            page().locator("#missing").click();
        }
    }

    public static class ChecksViewportTest extends BaseUiTest {
        @org.testng.annotations.Test
        public void checksViewport() {
            ProbePage probe = open(ProbePage.class);
            org.testng.Assert.assertEquals(probe.width(), 800);
            org.testng.Assert.assertEquals(probe.height(), 600);
        }
    }

    public static class ClicksWhatIsNotThereTest extends BaseUiTest {
        @org.testng.annotations.Test
        public void clicksWhatIsNotThere() {
            open(ProbePage.class).clickMissingButton();
        }
    }

    public static class ChecksBrowserTest extends BaseUiTest {
        @org.testng.annotations.Test
        public void runsInTheNamedBrowser(ITestContext context) {
            String browser = context.getCurrentXmlTest().getParameter("browser");
            String agent = open(ProbePage.class).userAgent();
            org.testng.Assert.assertTrue(agent.contains(browser.equals("firefox") ? "Firefox" : "Chrome"), agent);
        }
    }

    public static class RunsOnStagingTest extends BaseUiTest {
        @org.testng.annotations.Test
        public void runsOnStaging() {
            org.testng.Assert.assertEquals(environment(), "staging");
            org.testng.Assert.assertEquals(open(ProbePage.class).width(), 640);
        }
    }
}
