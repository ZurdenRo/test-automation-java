package com.company.qa.ui;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretsTest {

    private static TestSite site;

    private static final String QA_SECRET = "ui:\n  apps:\n    shop:\n      secrets:\n        password: SHOP_QA_ADMIN_PASSWORD\n";
    private static final String DEV_SECRET = "ui:\n  apps:\n    shop:\n      secrets:\n        password: SHOP_DEV_ADMIN_PASSWORD\n";

    @TempDir
    Path classpathDir;

    private final ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();

    @BeforeAll
    static void startSite() {
        site = new TestSite("shop");
    }

    @AfterAll
    static void stopSite() {
        site.close();
    }

    @AfterEach
    void restoreClassLoader() {
        Thread.currentThread().setContextClassLoader(originalLoader);
    }

    @Test
    void theEnvironmentsFileNamesTheVariableThatHoldsTheSecret() throws IOException {
        write("test-automation-qa.yaml", QA_SECRET);

        UiConfig config = UiConfigLoader.load(Map.of(),
                Map.of("SHOP_QA_ADMIN_PASSWORD", "qa-secret", "SHOP_DEV_ADMIN_PASSWORD", "dev-secret"));

        assertEquals("qa-secret", config.secret("shop", "password"));
    }

    @Test
    void theSameSecretNameReadsAnotherVariableInAnotherEnvironment() throws IOException {
        write("test-automation-qa.yaml", QA_SECRET);
        write("test-automation-dev.yaml", DEV_SECRET);

        UiConfig config = UiConfigLoader.load(Map.of("env", "dev"),
                Map.of("SHOP_QA_ADMIN_PASSWORD", "qa-secret", "SHOP_DEV_ADMIN_PASSWORD", "dev-secret"));

        assertEquals("dev-secret", config.secret("shop", "password"));
    }

    @Test
    void aSecretTheFileDoesNotMapIsNamedWithTheKeyToAdd() throws IOException {
        write("test-automation-qa.yaml", "");
        UiConfig config = UiConfigLoader.load(Map.of(), Map.of());

        UiConfigException e = assertThrows(UiConfigException.class, () -> config.secret("shop", "password"));

        assertEquals("Missing 'ui.apps.shop.secrets.password' in test-automation-qa.yaml: it names the environment"
                + " variable that holds secret 'password'", e.getMessage());
    }

    @Test
    void anUnsetVariableIsNamedWithoutRevealingAnything() throws IOException {
        write("test-automation-qa.yaml", QA_SECRET);
        UiConfig config = UiConfigLoader.load(Map.of(), Map.of("SHOP_QA_ADMIN_PASSWORD", "  "));

        UiConfigException e = assertThrows(UiConfigException.class, () -> config.secret("shop", "password"));

        assertEquals("Secret 'password' needs environment variable SHOP_QA_ADMIN_PASSWORD, which is not set"
                + " (blank values count as missing; test-automation-qa.yaml names it)", e.getMessage());
    }

    @Test
    void aUiTestReadsItsSecretsFromTheProcessEnvironment() {
        ConsumerRun.in(classpathDir).config("apps.shop.secrets.path", "PATH")
                .run(ReadsPathTest.class)
                .assertAllPassed();
    }

    @Test
    void aUiTestWhoseVariableIsUnsetFailsNamingIt() {
        ConsumerRun.Results results = ConsumerRun.in(classpathDir)
                .config("apps.shop.secrets.password", "QA_UI_SECRETS_TEST_SURELY_UNSET")
                .run(ReadsUnsetSecretTest.class);

        assertTrue(results.failure("readsIt").getMessage()
                .startsWith("Secret 'password' needs environment variable QA_UI_SECRETS_TEST_SURELY_UNSET"));
    }

    private void write(String name, String content) throws IOException {
        Files.writeString(classpathDir.resolve(name), content);
        Thread.currentThread().setContextClassLoader(
                new URLClassLoader(new URL[]{classpathDir.toUri().toURL()}, originalLoader));
    }

    @Test
    void aPageObjectReadsTheSecretsOfItsOwnApp() {
        ConsumerRun.in(classpathDir).config("apps.shop.secrets.password", "PATH")
                .config("apps.billing.secrets.password", "QA_UI_SECRETS_TEST_SURELY_UNSET")
                .run(PageReadsPathTest.class)
                .assertAllPassed();
    }

    @Test
    void aPageObjectWithoutAnAppSaysWhichMethodToOverride() {
        ConsumerRun.Results results = ConsumerRun.in(classpathDir).run(AppLessPageTest.class);

        assertEquals("AppLessPage names no app: override app() so that secret(\"password\") knows whose"
                + " ui.apps.<app>.secrets to read", results.failure("readsIt").getMessage());
    }

    @Test
    void aPageObjectCreatedWithNewCannotReachTheConfig() {
        ConsumerRun.Results results = ConsumerRun.in(classpathDir).run(HandMadePageTest.class);

        assertEquals("ShopLoginPage was created with new: create page objects with open(...) or on(...) so that"
                + " secret(\"password\") can reach the config", results.failure("readsIt").getMessage());
    }

    public static class ShopLoginPage extends BasePage {
        public ShopLoginPage(com.microsoft.playwright.Page page) {
            super(page);
        }

        @Override
        protected String app() {
            return "shop";
        }

        public String password() {
            return secret("password");
        }

        public com.microsoft.playwright.Page tab() {
            return page();
        }
    }

    public static class AppLessPage extends BasePage {
        public AppLessPage(com.microsoft.playwright.Page page) {
            super(page);
        }

        public String password() {
            return secret("password");
        }
    }

    public static class PageReadsPathTest extends BaseUiTest {
        @org.testng.annotations.Test
        public void readsIt() {
            assertFalse(on(ShopLoginPage.class).password().isEmpty());
        }
    }

    public static class AppLessPageTest extends BaseUiTest {
        @org.testng.annotations.Test
        public void readsIt() {
            on(AppLessPage.class).password();
        }
    }

    public static class HandMadePageTest extends BaseUiTest {
        @org.testng.annotations.Test
        public void readsIt() {
            new ShopLoginPage(on(ShopLoginPage.class).tab()).password();
        }
    }

    public static class ReadsPathTest extends BaseUiTest {
        @org.testng.annotations.Test
        public void readsIt() {
            assertFalse(secret("shop", "path").isEmpty());
        }
    }

    public static class ReadsUnsetSecretTest extends BaseUiTest {
        @org.testng.annotations.Test
        public void readsIt() {
            secret("shop", "password");
        }
    }
}
