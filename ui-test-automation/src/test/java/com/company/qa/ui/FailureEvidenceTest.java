package com.company.qa.ui;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

class FailureEvidenceTest {

    private static TestSite site;

    @TempDir
    Path classpathDir;

    @TempDir
    Path evidenceDir;

    @BeforeAll
    static void startSite() {
        site = new TestSite("appA");
    }

    @AfterAll
    static void stopSite() {
        site.close();
    }

    @Test
    void aFailingTestLeavesAScreenshotOfEachTabAndAPassingOneNothing() throws IOException {
        run("off", PassesAndFailsTest.class);

        String dir = PassesAndFailsTest.class.getName();
        assertEquals(List.of(dir + "/fails-tab2.png", dir + "/fails.png"), evidence());
        assertTrue(Files.size(evidenceDir.resolve(dir + "/fails.png")) > 0);
    }

    @Test
    void aTraceIsKeptForAFailingTestWhenConfigured() throws IOException {
        run("on-failure", PassesAndFailsTest.class);

        String dir = PassesAndFailsTest.class.getName();
        assertEquals(List.of(dir + "/fails-tab2.png", dir + "/fails-trace.zip", dir + "/fails.png"), evidence());
    }

    @Test
    void repeatedInvocationsOfAMethodKeepTheirOwnEvidence() throws IOException {
        run("off", FailsTwiceTest.class);

        String dir = FailsTwiceTest.class.getName();
        assertEquals(List.of(dir + "/failsTwice-2.png", dir + "/failsTwice.png"), evidence());
    }

    private void run(String trace, Class<?> test) {
        ConsumerRun.in(classpathDir).config("apps.shop", site.url())
                .config("evidence-dir", evidenceDir.toString().replace('\\', '/'))
                .config("trace", trace)
                .run(test);
    }

    private List<String> evidence() throws IOException {
        try (Stream<Path> files = Files.walk(evidenceDir)) {
            return files.filter(Files::isRegularFile)
                    .map(p -> evidenceDir.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    public static class AnyPage extends ShopPage {
        public AnyPage(Page page) {
            super(page);
        }

        @Override
        protected String path() {
            return "/any";
        }
    }

    public static class PassesAndFailsTest extends BaseUiTest {
        @org.testng.annotations.Test
        public void passes() {
            open(AnyPage.class);
        }

        @org.testng.annotations.Test
        public void fails() {
            open(AnyPage.class);
            openInNewTab(AnyPage.class);
            throw new AssertionError("expected failure");
        }
    }

    public static class FailsTwiceTest extends BaseUiTest {
        @org.testng.annotations.Test(invocationCount = 2)
        public void failsTwice() {
            open(AnyPage.class);
            throw new AssertionError("expected failure");
        }
    }
}
