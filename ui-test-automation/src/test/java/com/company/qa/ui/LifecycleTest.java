package com.company.qa.ui;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.testng.Assert.assertEquals;

class LifecycleTest {

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
    void eachTestMethodGetsAFreshBrowserContext() {
        ConsumerRun.in(classpathDir).config("base.url", site.url())
                .run(LeavesACookieBehind.class)
                .assertAllPassed();
    }

    @Test
    void aSecondSuiteInTheSameJvmLaunchesItsOwnBrowser() {
        ConsumerRun run = ConsumerRun.in(classpathDir).config("base.url", site.url());
        run.run(LeavesACookieBehind.class).assertAllPassed();
        run.run(LeavesACookieBehind.class).assertAllPassed();
    }

    @Test
    void parallelMethodsEachGetTheirOwnContext() {
        ConsumerRun.Results results = ConsumerRun.in(classpathDir).config("base.url", site.url())
                .parallelMethods()
                .run(SetsItsOwnCookie.class)
                .assertAllPassed();

        assertEquals(results.passed().size(), 4);
    }

    public static class CookiePage extends BasePage {
        public CookiePage(Page page) {
            super(page);
        }

        @Override
        protected String path() {
            return "/cookies";
        }

        public void remember(String value) {
            page().evaluate("v => document.cookie = 'who=' + v", value);
        }

        public String remembered() {
            return (String) page().evaluate("() => document.cookie");
        }
    }

    public static class LeavesACookieBehind extends BaseUiTest {
        @org.testng.annotations.Test(priority = 1)
        public void setsACookie() {
            CookiePage page = open(CookiePage.class);
            page.remember("first");
            assertEquals(page.remembered(), "who=first");
        }

        @org.testng.annotations.Test(priority = 2)
        public void doesNotSeeIt() {
            assertEquals(open(CookiePage.class).remembered(), "");
        }
    }

    public static class SetsItsOwnCookie extends BaseUiTest {
        @org.testng.annotations.Test
        public void first() throws InterruptedException {
            remembersOnly("first");
        }

        @org.testng.annotations.Test
        public void second() throws InterruptedException {
            remembersOnly("second");
        }

        @org.testng.annotations.Test
        public void third() throws InterruptedException {
            remembersOnly("third");
        }

        @org.testng.annotations.Test
        public void fourth() throws InterruptedException {
            remembersOnly("fourth");
        }

        private void remembersOnly(String mine) throws InterruptedException {
            CookiePage page = open(CookiePage.class);
            page.remember(mine);
            Thread.sleep(300);                                      // let the other threads write theirs
            assertEquals(page.remembered(), "who=" + mine);
        }
    }
}
