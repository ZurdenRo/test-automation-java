package com.company.qa.ui;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AppsTest {

    private static TestSite appA;
    private static TestSite appB;

    @TempDir
    Path classpathDir;

    @BeforeAll
    static void startSites() {
        appA = new TestSite("appA");
        appB = new TestSite("appB");
    }

    @AfterAll
    static void stopSites() {
        appA.close();
        appB.close();
    }

    @Test
    void aTestMovesBetweenAppsInOneTabByOpeningTheirPages() {
        ConsumerRun.in(classpathDir)
                .config("base.url", appA.url())
                .config("app.billing.url", appB.url())
                .run(GoesFromAToBAndBackTest.class)
                .assertAllPassed();
    }

    @Test
    void aPageOfAnAppWithoutAUrlNamesTheSettingToAdd() {
        ConsumerRun.Results results = ConsumerRun.in(classpathDir)
                .config("base.url", appA.url())
                .run(OpensInvoiceTest.class);

        assertEquals("Missing 'app.billing.url' for InvoicePage (blank values count as missing): looked in"
                        + " environment variable QA_UI_APP_BILLING_URL, ui-test.properties",
                results.failure("opensInvoice").getMessage());
    }

    public static class HomePage extends BasePage {
        public HomePage(Page page) {
            super(page);
        }

        @Override
        protected String path() {
            return "/home";
        }

        public Locator appName() {
            return page().locator("#app");
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

    public static class GoesFromAToBAndBackTest extends BaseUiTest {
        @org.testng.annotations.Test
        public void goesFromAToBAndBack() {
            assertThat(open(HomePage.class).appName()).hasText("appA");
            assertThat(open(InvoicePage.class).appName()).hasText("appB");
            assertThat(open(HomePage.class).appName()).hasText("appA");
        }
    }

    public static class OpensInvoiceTest extends BaseUiTest {
        @org.testng.annotations.Test
        public void opensInvoice() {
            open(InvoicePage.class);
        }
    }
}
