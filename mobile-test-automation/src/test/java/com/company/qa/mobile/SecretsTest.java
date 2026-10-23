package com.company.qa.mobile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecretsTest {

    private static final String BASE = "appium.server.url=http://localhost:4723\n"
            + "android.app=/a.apk\nandroid.devices=emulator-5554\n";

    @TempDir
    Path classpathDir;

    private final ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();

    @AfterEach
    void restoreClassLoader() {
        Thread.currentThread().setContextClassLoader(originalLoader);
    }

    @Test
    void theEnvironmentsFileNamesTheVariableThatHoldsTheSecret() throws IOException {
        write("mobile-test-qa.properties", BASE + "secret.admin.password=SHOP_QA_ADMIN_PASSWORD\n");

        MobileConfig config = MobileConfigLoader.load(Map.of(),
                Map.of("SHOP_QA_ADMIN_PASSWORD", "qa-secret", "SHOP_DEV_ADMIN_PASSWORD", "dev-secret"));

        assertEquals("qa-secret", config.secret("admin.password"));
    }

    @Test
    void theSameSecretNameReadsAnotherVariableInAnotherEnvironment() throws IOException {
        write("mobile-test-qa.properties", BASE + "secret.admin.password=SHOP_QA_ADMIN_PASSWORD\n");
        write("mobile-test-dev.properties", BASE + "secret.admin.password=SHOP_DEV_ADMIN_PASSWORD\n");

        MobileConfig config = MobileConfigLoader.load(Map.of("env", "dev"),
                Map.of("SHOP_QA_ADMIN_PASSWORD", "qa-secret", "SHOP_DEV_ADMIN_PASSWORD", "dev-secret"));

        assertEquals("dev-secret", config.secret("admin.password"));
    }

    @Test
    void aSecretTheFileDoesNotNameSaysWhichKeyToAdd() throws IOException {
        write("mobile-test-qa.properties", BASE);
        MobileConfig config = MobileConfigLoader.load(Map.of(), Map.of());

        MobileConfigException e = assertThrows(MobileConfigException.class, () -> config.secret("admin.password"));

        assertEquals("Missing 'secret.admin.password' in mobile-test-qa.properties: it names the environment"
                + " variable that holds secret 'admin.password'", e.getMessage());
    }

    @Test
    void anUnsetVariableNamesItButNeverAValue() throws IOException {
        write("mobile-test-qa.properties", BASE + "secret.admin.password=SHOP_QA_ADMIN_PASSWORD\n");
        MobileConfig config = MobileConfigLoader.load(Map.of(), Map.of("SHOP_QA_ADMIN_PASSWORD", " "));

        MobileConfigException e = assertThrows(MobileConfigException.class, () -> config.secret("admin.password"));

        assertEquals("Secret 'admin.password' needs environment variable SHOP_QA_ADMIN_PASSWORD, which is not set"
                + " (blank values count as missing; mobile-test-qa.properties names it)", e.getMessage());
    }

    @Test
    void aSettingCannotBeOverriddenByAnEnvironmentVariable() throws IOException {
        write("mobile-test-qa.properties", BASE);

        MobileConfig config = MobileConfigLoader.load(Map.of(),
                Map.of("APPIUM_SERVER_URL", "http://evil:4723", "QA_MOBILE_WAIT_UNTIL_TIMEOUT_MS", "1"));

        assertEquals("http://localhost:4723", config.appiumServerUrl().toString());
        assertEquals(10_000, config.waitUntilTimeoutMs());
    }

    private void write(String name, String content) throws IOException {
        Files.writeString(classpathDir.resolve(name), content);
        try {
            Thread.currentThread().setContextClassLoader(
                    new URLClassLoader(new URL[]{classpathDir.toUri().toURL()}, originalLoader));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
