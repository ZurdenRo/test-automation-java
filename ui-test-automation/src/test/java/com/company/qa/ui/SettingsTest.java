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
        ConsumerRun.in(classpathDir).config("base.url", site.url())
                .config("viewport.width", "800").config("viewport.height", "600")
                .run(ChecksViewport.class)
                .assertAllPassed();
    }

    @Test
    void theTimeoutBoundsHowLongAnActionWaits() {
        ConsumerRun.Results results = ConsumerRun.in(classpathDir).config("base.url", site.url())
                .config("timeout.ms", "300")
                .run(ClicksWhatIsNotThere.class);

        assertTrue(results.failure("clicksWhatIsNotThere").getMessage().contains("Timeout 300ms"),
                results.summary());
    }

    @Test
    void eachXmlTestRunsInTheBrowserItsParametersName() {
        ConsumerRun.Results results = ConsumerRun.in(classpathDir).config("base.url", site.url())
                .xmlTest("on chromium", Map.of("browser", "chromium"))
                .xmlTest("on firefox", Map.of("browser", "firefox"))
                .run(ChecksBrowser.class)
                .assertAllPassed();

        assertEquals(List.of("runsInTheNamedBrowser", "runsInTheNamedBrowser"), results.passed());
    }

    @Test
    void aTestCanNameItsOwnConfigResource() {
        ConsumerRun.in(classpathDir)
                .resource("shop.properties", "base.url=" + site.url() + "\nviewport.width=640\nviewport.height=480\n")
                .run(UsesShopConfig.class)
                .assertAllPassed();
    }

    public static class ProbePage extends BasePage {
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

    public static class ChecksViewport extends BaseUiTest {
        @org.testng.annotations.Test
        public void checksViewport() {
            ProbePage probe = open(ProbePage.class);
            org.testng.Assert.assertEquals(probe.width(), 800);
            org.testng.Assert.assertEquals(probe.height(), 600);
        }
    }

    public static class ClicksWhatIsNotThere extends BaseUiTest {
        @org.testng.annotations.Test
        public void clicksWhatIsNotThere() {
            open(ProbePage.class).clickMissingButton();
        }
    }

    public static class ChecksBrowser extends BaseUiTest {
        @org.testng.annotations.Test
        public void runsInTheNamedBrowser(ITestContext context) {
            String browser = context.getCurrentXmlTest().getParameter("browser");
            String agent = open(ProbePage.class).userAgent();
            org.testng.Assert.assertTrue(agent.contains(browser.equals("firefox") ? "Firefox" : "Chrome"), agent);
        }
    }

    public static class UsesShopConfig extends BaseUiTest {
        @Override
        protected String configResource() {
            return "shop.properties";
        }

        @org.testng.annotations.Test
        public void usesShopConfig() {
            org.testng.Assert.assertEquals(open(ProbePage.class).width(), 640);
        }
    }
}
