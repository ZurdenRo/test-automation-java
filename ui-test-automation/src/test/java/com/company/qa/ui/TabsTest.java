package com.company.qa.ui;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

class TabsTest {

    private static TestSite appA;
    private static TestSite appB;

    @TempDir
    Path classpathDir;

    @BeforeAll
    static void startSites() {
        appA = new TestSite("appA").page("/home", "<a id='go' href='/dashboard'>Dashboard</a>");
        appB = new TestSite("appB");
    }

    @AfterAll
    static void stopSites() {
        appA.close();
        appB.close();
    }

    @Test
    void onWrapsTheScreenTheAppReachedWithoutNavigating() {
        run(FollowsALink.class).assertAllPassed();
    }

    @Test
    void eachPageObjectActsOnTheTabItWasOpenedOn() {
        run(UsesTwoTabs.class).assertAllPassed();
    }

    private ConsumerRun.Results run(Class<?> test) {
        return ConsumerRun.in(classpathDir)
                .config("base.url", appA.url())
                .config("app.billing.url", appB.url())
                .run(test);
    }

    public static class HomePage extends BasePage {
        public HomePage(Page page) {
            super(page);
        }

        @Override
        protected String path() {
            return "/home";
        }

        public void followLink() {
            page().locator("#go").click();
        }

        public Locator shownPath() {
            return page().locator("#path");
        }
    }

    public static class DashboardPage extends BasePage {
        public DashboardPage(Page page) {
            super(page);
        }

        public Locator shownPath() {
            return page().locator("#path");
        }
    }

    public static class InvoicePage extends BasePage {
        public InvoicePage(Page page) {
            super(page);
        }

        @Override
        protected String app() {
            return "billing";
        }

        @Override
        protected String path() {
            return "/invoices";
        }

        public Locator appName() {
            return page().locator("#app");
        }
    }

    public static class FollowsALink extends BaseUiTest {
        @org.testng.annotations.Test
        public void followsALink() {
            open(HomePage.class).followLink();
            assertThat(on(DashboardPage.class).shownPath()).hasText("/dashboard");
        }
    }

    public static class UsesTwoTabs extends BaseUiTest {
        @org.testng.annotations.Test
        public void usesTwoTabs() {
            HomePage home = open(HomePage.class);
            InvoicePage invoice = openInNewTab(InvoicePage.class);

            assertThat(invoice.appName()).hasText("appB");
            home.followLink();                                     // acts on the first tab, no switching
            assertThat(on(DashboardPage.class).shownPath()).hasText("/dashboard");
            assertThat(invoice.appName()).hasText("appB");         // the second tab did not move
        }
    }
}
