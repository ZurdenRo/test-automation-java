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

    private static final String QA_SECRET = "ui:\n  secrets:\n    admin-password: SHOP_QA_ADMIN_PASSWORD\n";
    private static final String DEV_SECRET = "ui:\n  secrets:\n    admin-password: SHOP_DEV_ADMIN_PASSWORD\n";

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

        assertEquals("qa-secret", config.secret("admin-password"));
    }

    @Test
    void theSameSecretNameReadsAnotherVariableInAnotherEnvironment() throws IOException {
        write("test-automation-qa.yaml", QA_SECRET);
        write("test-automation-dev.yaml", DEV_SECRET);

        UiConfig config = UiConfigLoader.load(Map.of("env", "dev"),
                Map.of("SHOP_QA_ADMIN_PASSWORD", "qa-secret", "SHOP_DEV_ADMIN_PASSWORD", "dev-secret"));

        assertEquals("dev-secret", config.secret("admin-password"));
    }

    @Test
    void aSecretTheFileDoesNotMapIsNamedWithTheKeyToAdd() throws IOException {
        write("test-automation-qa.yaml", "");
        UiConfig config = UiConfigLoader.load(Map.of(), Map.of());

        UiConfigException e = assertThrows(UiConfigException.class, () -> config.secret("admin-password"));

        assertEquals("Missing 'ui.secrets.admin-password' in test-automation-qa.yaml: it names the environment"
                + " variable that holds secret 'admin-password'", e.getMessage());
    }

    @Test
    void anUnsetVariableIsNamedWithoutRevealingAnything() throws IOException {
        write("test-automation-qa.yaml", QA_SECRET);
        UiConfig config = UiConfigLoader.load(Map.of(), Map.of("SHOP_QA_ADMIN_PASSWORD", "  "));

        UiConfigException e = assertThrows(UiConfigException.class, () -> config.secret("admin-password"));

        assertEquals("Secret 'admin-password' needs environment variable SHOP_QA_ADMIN_PASSWORD, which is not set"
                + " (blank values count as missing; test-automation-qa.yaml names it)", e.getMessage());
    }

    @Test
    void aUiTestReadsItsSecretsFromTheProcessEnvironment() {
        ConsumerRun.in(classpathDir).config("secrets.search-path", "PATH")
                .run(ReadsPathTest.class)
                .assertAllPassed();
    }

    @Test
    void aUiTestWhoseVariableIsUnsetFailsNamingIt() {
        ConsumerRun.Results results = ConsumerRun.in(classpathDir)
                .config("secrets.admin-password", "QA_UI_SECRETS_TEST_SURELY_UNSET")
                .run(ReadsUnsetSecretTest.class);

        assertTrue(results.failure("readsIt").getMessage()
                .startsWith("Secret 'admin-password' needs environment variable QA_UI_SECRETS_TEST_SURELY_UNSET"));
    }

    private void write(String name, String content) throws IOException {
        Files.writeString(classpathDir.resolve(name), content);
        Thread.currentThread().setContextClassLoader(
                new URLClassLoader(new URL[]{classpathDir.toUri().toURL()}, originalLoader));
    }

    public static class ReadsPathTest extends BaseUiTest {
        @org.testng.annotations.Test
        public void readsIt() {
            assertFalse(secret("search-path").isEmpty());
        }
    }

    public static class ReadsUnsetSecretTest extends BaseUiTest {
        @org.testng.annotations.Test
        public void readsIt() {
            secret("admin-password");
        }
    }
}
