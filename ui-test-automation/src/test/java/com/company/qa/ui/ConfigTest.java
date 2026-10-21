package com.company.qa.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {

    @TempDir
    Path classpathDir;

    private final ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();

    @AfterEach
    void restoreClassLoader() {
        Thread.currentThread().setContextClassLoader(originalLoader);
    }

    @Test
    void defaultsApplyWhenNoSourceSetsAValue() throws Exception {
        UiConfig config = load("base.url=http://app\n", Map.of(), Map.of());

        assertEquals("chromium", config.browser());
        assertNull(config.channel());
        assertTrue(config.headless());
        assertEquals(30_000, config.timeoutMs());
        assertEquals(1280, config.viewportWidth());
        assertEquals(720, config.viewportHeight());
        assertEquals(0, config.slowMoMs());
        assertEquals(Path.of("ui-evidence"), config.evidenceDir());
        assertFalse(config.traceOnFailure());
    }

    @Test
    void theFileSetsEverySetting() throws Exception {
        UiConfig config = load("browser=firefox\nchannel=chrome\nheadless=false\ntimeout.ms=500\n"
                + "viewport.width=800\nviewport.height=600\nslow.mo.ms=50\nevidence.dir=out/evidence\n"
                + "trace=on-failure\n", Map.of(), Map.of());

        assertEquals("firefox", config.browser());
        assertEquals("chrome", config.channel());
        assertFalse(config.headless());
        assertEquals(500, config.timeoutMs());
        assertEquals(800, config.viewportWidth());
        assertEquals(600, config.viewportHeight());
        assertEquals(50, config.slowMoMs());
        assertEquals(Path.of("out/evidence"), config.evidenceDir());
        assertTrue(config.traceOnFailure());
    }

    @Test
    void anEnvironmentVariableBeatsTheFile() throws Exception {
        UiConfig config = load("headless=false\ntimeout.ms=500\napp.billing.url=http://file\n",
                Map.of("QA_UI_HEADLESS", "true", "QA_UI_TIMEOUT_MS", "900", "QA_UI_APP_BILLING_URL", "http://env"),
                Map.of());

        assertTrue(config.headless());
        assertEquals(900, config.timeoutMs());
        assertEquals("http://env", config.appUrl("billing", "InvoicePage"));
    }

    @Test
    void anXmlParameterBeatsTheEnvironmentForBrowserChannelHeadlessAndViewport() throws Exception {
        UiConfig config = load("", Map.of("QA_UI_BROWSER", "chromium", "QA_UI_HEADLESS", "true"),
                Map.of("browser", "firefox", "channel", "msedge", "headless", "false",
                        "viewport.width", "640", "viewport.height", "480"));

        assertEquals("firefox", config.browser());
        assertEquals("msedge", config.channel());
        assertFalse(config.headless());
        assertEquals(640, config.viewportWidth());
        assertEquals(480, config.viewportHeight());
    }

    @Test
    void xmlParametersForOtherSettingsAreIgnored() throws Exception {
        UiConfig config = load("timeout.ms=500\n", Map.of(), Map.of("timeout.ms", "900", "base.url", "http://xml"));

        assertEquals(500, config.timeoutMs());
        assertThrows(UiConfigException.class, () -> config.appUrl(null, "HomePage"));
    }

    @Test
    void anUnknownBrowserListsTheSupportedOnes() throws Exception {
        UiConfigException e = assertThrows(UiConfigException.class,
                () -> load("", Map.of("QA_UI_BROWSER", "opera"), Map.of()));

        assertEquals("'browser' from environment variable QA_UI_BROWSER is 'opera': expected chromium, firefox"
                + " or webkit", e.getMessage());
    }

    @Test
    void anUnknownChannelListsTheSupportedOnes() throws Exception {
        UiConfigException e = assertThrows(UiConfigException.class,
                () -> load("", Map.of(), Map.of("channel", "safari")));

        assertEquals("'channel' from <parameter> channel is 'safari': expected chrome or msedge", e.getMessage());
    }

    @Test
    void aNonNumericTimeoutSaysWhereItCameFrom() throws Exception {
        UiConfigException e = assertThrows(UiConfigException.class, () -> load("timeout.ms=soon\n", Map.of(), Map.of()));

        assertEquals("'timeout.ms' from ui-test.properties is 'soon': expected a positive whole number",
                e.getMessage());
    }

    @Test
    void anUnknownTraceModeListsTheSupportedOnes() throws Exception {
        UiConfigException e = assertThrows(UiConfigException.class, () -> load("trace=always\n", Map.of(), Map.of()));

        assertEquals("'trace' from ui-test.properties is 'always': expected off or on-failure", e.getMessage());
    }

    @Test
    void aMissingConfigResourceIsNamed() {
        Thread.currentThread().setContextClassLoader(classLoader());

        UiConfigException e = assertThrows(UiConfigException.class,
                () -> UiConfigLoader.load("other.properties", Map.of(), Map.of()));

        assertEquals("Config resource 'other.properties' not found on the classpath", e.getMessage());
    }

    private UiConfig load(String file, Map<String, String> env, Map<String, String> xmlParameters) throws Exception {
        Files.writeString(classpathDir.resolve("ui-test.properties"), file);
        Thread.currentThread().setContextClassLoader(classLoader());
        return UiConfigLoader.load("ui-test.properties", env, xmlParameters);
    }

    private ClassLoader classLoader() {
        try {
            return new URLClassLoader(new URL[]{classpathDir.toUri().toURL()}, originalLoader);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
