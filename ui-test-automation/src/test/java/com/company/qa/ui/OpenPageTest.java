package com.company.qa.ui;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenPageTest {

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
    void openNavigatesToThePageObjectsPathOnItsApp() {
        ConsumerRun.in(classpathDir).config("apps.shop.url", site.url())
                .run(OpensLoginPageTest.class)
                .assertAllPassed();
    }

    @Test
    void openingAPageWithoutAPathSaysHowToReachIt() {
        ConsumerRun.Results results = ConsumerRun.in(classpathDir).config("apps.shop.url", site.url())
                .run(OpensDashboardPageTest.class);

        assertEquals(List.of("opensDashboard"), results.failed());
        assertEquals("DashboardPage has no path: reach it from another page, or use on(DashboardPage.class)",
                results.failure("opensDashboard").getMessage());
    }

    @Test
    void aPageObjectWithoutAPageConstructorIsNamed() {
        ConsumerRun.Results results = ConsumerRun.in(classpathDir).config("apps.shop.url", site.url())
                .run(OpensNoConstructorPageTest.class);

        assertEquals("NoConstructorPage needs a public constructor taking a Page",
                results.failure("opensIt").getMessage());
    }

    @Test
    void openingAPageWithAPathButNoAppSaysToNameIt() {
        ConsumerRun.Results results = ConsumerRun.in(classpathDir).config("apps.shop.url", site.url())
                .run(OpensAppLessPageTest.class);

        assertEquals("AppLessPage has a path but no app: override app() to name the app whose"
                + " ui.apps.<name>.url it lives under", results.failure("opensIt").getMessage());
    }

    public static class AppLessPage extends BasePage {
        public AppLessPage(Page page) {
            super(page);
        }

        @Override
        protected String path() {
            return "/somewhere";
        }
    }

    public static class OpensAppLessPageTest extends BaseUiTest {
        @org.testng.annotations.Test
        public void opensIt() {
            open(AppLessPage.class);
        }
    }

    public static class NoConstructorPage extends ShopPage {
        NoConstructorPage(Page page) {
            super(page);
        }

        @Override
        protected String path() {
            return "/x";
        }
    }

    public static class OpensNoConstructorPageTest extends BaseUiTest {
        @org.testng.annotations.Test
        public void opensIt() {
            open(NoConstructorPage.class);
        }
    }

    public static class DashboardPage extends ShopPage {
        public DashboardPage(Page page) {
            super(page);
        }
    }

    public static class OpensDashboardPageTest extends BaseUiTest {
        @org.testng.annotations.Test
        public void opensDashboard() {
            open(DashboardPage.class);
        }
    }

    public static class LoginPage extends ShopPage {
        public LoginPage(Page page) {
            super(page);
        }

        @Override
        protected String path() {
            return "/login";
        }

        public Locator appName() {
            return page().locator("#app");
        }

        public Locator shownPath() {
            return page().locator("#path");
        }
    }

    public static class OpensLoginPageTest extends BaseUiTest {
        @org.testng.annotations.Test
        public void opensLogin() {
            LoginPage login = open(LoginPage.class);
            assertThat(login.appName()).hasText("appA");
            assertThat(login.shownPath()).hasText("/login");
        }
    }
}
