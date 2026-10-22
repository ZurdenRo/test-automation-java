package com.company.qa.ui;

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
    void defaultsApplyWhenTheFileSetsNothing() throws Exception {
        UiConfig config = load("", Map.of());

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
                + "trace=on-failure\napp.shop.url=http://shop\n", Map.of());

        assertEquals("firefox", config.browser());
        assertEquals("chrome", config.channel());
        assertFalse(config.headless());
        assertEquals(500, config.timeoutMs());
        assertEquals(800, config.viewportWidth());
        assertEquals(600, config.viewportHeight());
        assertEquals(50, config.slowMoMs());
        assertEquals(Path.of("out/evidence"), config.evidenceDir());
        assertTrue(config.traceOnFailure());
        assertEquals("http://shop", config.appUrl("shop", "HomePage"));
    }

    @Test
    void theDefaultFileIsTheQaOne() throws Exception {
        write("ui-test-qa.properties", "app.shop.url=http://qa\n");

        assertEquals("http://qa", UiConfigLoader.load(Map.of()).appUrl("shop", "HomePage"));
    }

    @Test
    void theSuiteNamesTheFileOfItsEnvironment() throws Exception {
        write("ui-test-qa.properties", "app.shop.url=http://qa\n");
        write("ui-test-staging.properties", "app.shop.url=http://staging\n");

        UiConfig config = UiConfigLoader.load(Map.of("ui.config", "ui-test-staging.properties"));

        assertEquals("http://staging", config.appUrl("shop", "HomePage"));
    }

    @Test
    void anXmlParameterBeatsTheFileForBrowserChannelHeadlessAndViewport() throws Exception {
        UiConfig config = load("browser=chromium\nheadless=true\n",
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
        UiConfig config = load("timeout.ms=500\n", Map.of("timeout.ms", "900", "app.shop.url", "http://xml"));

        assertEquals(500, config.timeoutMs());
        assertThrows(UiConfigException.class, () -> config.appUrl("shop", "HomePage"));
    }

    @Test
    void aMissingAppUrlNamesTheFileToAddItTo() throws Exception {
        UiConfig config = load("", Map.of());

        UiConfigException e = assertThrows(UiConfigException.class, () -> config.appUrl("billing", "InvoicePage"));

        assertEquals("Missing 'app.billing.url' for InvoicePage in ui-test-qa.properties"
                + " (blank values count as missing)", e.getMessage());
    }

    @Test
    void anUnknownBrowserListsTheSupportedOnes() throws Exception {
        UiConfigException e = assertThrows(UiConfigException.class, () -> load("browser=opera\n", Map.of()));

        assertEquals("'browser' from ui-test-qa.properties is 'opera': expected chromium, firefox or webkit",
                e.getMessage());
    }

    @Test
    void anUnknownChannelListsTheSupportedOnes() throws Exception {
        UiConfigException e = assertThrows(UiConfigException.class, () -> load("", Map.of("channel", "safari")));

        assertEquals("'channel' from <parameter> channel is 'safari': expected chrome or msedge", e.getMessage());
    }

    @Test
    void aNonNumericTimeoutSaysWhereItCameFrom() throws Exception {
        UiConfigException e = assertThrows(UiConfigException.class, () -> load("timeout.ms=soon\n", Map.of()));

        assertEquals("'timeout.ms' from ui-test-qa.properties is 'soon': expected a positive whole number",
                e.getMessage());
    }

    @Test
    void anUnknownTraceModeListsTheSupportedOnes() throws Exception {
        UiConfigException e = assertThrows(UiConfigException.class, () -> load("trace=always\n", Map.of()));

        assertEquals("'trace' from ui-test-qa.properties is 'always': expected off or on-failure", e.getMessage());
    }

    @Test
    void aMissingConfigResourceSaysWhichSuiteParameterNamesIt() {
        Thread.currentThread().setContextClassLoader(classLoader());

        UiConfigException e = assertThrows(UiConfigException.class,
                () -> UiConfigLoader.load(Map.of("ui.config", "ui-test-prod.properties")));

        assertEquals("Config resource 'ui-test-prod.properties' (from <parameter> ui.config) not found on the"
                + " classpath", e.getMessage());
    }

    @Test
    void aMissingDefaultConfigResourceSaysHowToNameAnother() {
        Thread.currentThread().setContextClassLoader(classLoader());

        UiConfigException e = assertThrows(UiConfigException.class, () -> UiConfigLoader.load(Map.of()));

        assertEquals("Config resource 'ui-test-qa.properties' (the default; name another with <parameter>"
                + " ui.config) not found on the classpath", e.getMessage());
    }

    private UiConfig load(String file, Map<String, String> xmlParameters) throws IOException {
        write("ui-test-qa.properties", file);
        return UiConfigLoader.load(xmlParameters);
    }

    private void write(String name, String content) throws IOException {
        Files.writeString(classpathDir.resolve(name), content);
        Thread.currentThread().setContextClassLoader(classLoader());
    }

    private ClassLoader classLoader() {
        try {
            return new URLClassLoader(new URL[]{classpathDir.toUri().toURL()}, originalLoader);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
