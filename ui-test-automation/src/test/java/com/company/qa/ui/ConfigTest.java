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
        UiConfig config = load("ui:\n  browser: firefox\n  channel: chrome\n  headless: false\n  timeout-ms: 500\n"
                + "  viewport: { width: 800, height: 600 }\n  slow-mo-ms: 50\n  evidence-dir: out/evidence\n"
                + "  trace: on-failure\n  apps:\n    shop:\n      url: http://shop\n", Map.of());

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
    void theDefaultEnvironmentsFileIsTheQaOne() throws Exception {
        write("test-automation-qa.yaml", apps("http://qa"));

        assertEquals("http://qa", UiConfigLoader.load(Map.of()).appUrl("shop", "HomePage"));
    }

    @Test
    void theDefaultEnvironmentIsQa() throws Exception {
        assertEquals("qa", load("", Map.of()).environment());
    }

    @Test
    void theSuiteNamesItsEnvironmentAndItsFileFollows() throws Exception {
        write("test-automation-qa.yaml", apps("http://qa"));
        write("test-automation-staging.yaml", apps("http://staging"));

        UiConfig config = UiConfigLoader.load(Map.of("env", "staging"));

        assertEquals("staging", config.environment());
        assertEquals("http://staging", config.appUrl("shop", "HomePage"));
    }

    @Test
    void anXmlParameterBeatsTheFileForBrowserChannelHeadlessAndViewport() throws Exception {
        UiConfig config = load("ui:\n  browser: chromium\n  headless: true\n",
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
        UiConfig config = load("ui:\n  timeout-ms: 500\n", Map.of("timeout-ms", "900", "apps.shop", "http://xml"));

        assertEquals(500, config.timeoutMs());
        assertThrows(UiConfigException.class, () -> config.appUrl("shop", "HomePage"));
    }

    @Test
    void aMissingAppUrlNamesTheFileToAddItTo() throws Exception {
        UiConfig config = load("", Map.of());

        UiConfigException e = assertThrows(UiConfigException.class, () -> config.appUrl("billing", "InvoicePage"));

        assertEquals("Missing 'ui.apps.billing.url' in test-automation-qa.yaml, needed by InvoicePage"
                + " (blank values count as missing)", e.getMessage());
    }

    @Test
    void anUnknownBrowserListsTheSupportedOnes() throws Exception {
        UiConfigException e = assertThrows(UiConfigException.class, () -> load("ui:\n  browser: opera\n", Map.of()));

        assertEquals("'ui.browser' in test-automation-qa.yaml is 'opera': expected chromium, firefox or webkit",
                e.getMessage());
    }

    @Test
    void anUnknownChannelListsTheSupportedOnes() throws Exception {
        UiConfigException e = assertThrows(UiConfigException.class, () -> load("", Map.of("channel", "safari")));

        assertEquals("'channel' in <parameter> channel is 'safari': expected chrome or msedge", e.getMessage());
    }

    @Test
    void aNonNumericTimeoutSaysWhereItCameFrom() throws Exception {
        UiConfigException e = assertThrows(UiConfigException.class, () -> load("ui:\n  timeout-ms: soon\n", Map.of()));

        assertEquals("'ui.timeout-ms' in test-automation-qa.yaml is 'soon': expected a positive whole number",
                e.getMessage());
    }

    @Test
    void anUnknownTraceModeListsTheSupportedOnes() throws Exception {
        UiConfigException e = assertThrows(UiConfigException.class, () -> load("ui:\n  trace: always\n", Map.of()));

        assertEquals("'ui.trace' in test-automation-qa.yaml is 'always': expected off or on-failure", e.getMessage());
    }

    @Test
    void aMissingConfigResourceSaysWhichEnvironmentNeedsIt() {
        Thread.currentThread().setContextClassLoader(classLoader());

        UiConfigException e = assertThrows(UiConfigException.class,
                () -> UiConfigLoader.load(Map.of("env", "prod")));

        assertEquals("Config file 'test-automation-prod.yaml' (for <parameter> env 'prod') not found on the"
                + " classpath", e.getMessage());
    }

    @Test
    void aMissingDefaultConfigResourceSaysHowToNameAnother() {
        Thread.currentThread().setContextClassLoader(classLoader());

        UiConfigException e = assertThrows(UiConfigException.class, () -> UiConfigLoader.load(Map.of()));

        assertEquals("Config file 'test-automation-qa.yaml' (for env 'qa', the default; name another with"
                + " <parameter> env) not found on the classpath", e.getMessage());
    }

    @Test
    void theEnvironmentsFileLaysOverTheSharedOne() throws Exception {
        write("test-automation.yaml", "ui:\n  browser: firefox\n  timeout-ms: 500\n  apps:\n    shop:\n      url: http://shared\n");
        write("test-automation-staging.yaml", "ui:\n  timeout-ms: 900\n");

        UiConfig config = UiConfigLoader.load(Map.of("env", "staging"));

        assertEquals("firefox", config.browser());
        assertEquals(900, config.timeoutMs());
        assertEquals("http://shared", config.appUrl("shop", "HomePage"));
    }

    @Test
    void aMisspelledSettingFailsNamingItAndItsFile() {
        UiConfigException e = assertThrows(UiConfigException.class, () -> load("ui:\n  heedless: true\n", Map.of()));

        assertTrue(e.getMessage().startsWith("Unknown key 'ui.heedless' in test-automation-qa.yaml"), e.getMessage());
    }

    @Test
    void aFileWithoutAUiSectionSetsNothing() throws Exception {
        assertEquals("chromium", load("services:\n  users:\n    base-url: http://u\n", Map.of()).browser());
    }

    @Test
    void anAppGivenAsAPlainValueFailsSayingItIsASection() {
        UiConfigException e = assertThrows(UiConfigException.class,
                () -> load("ui:\n  apps:\n    shop: http://shop\n", Map.of()));

        assertEquals("'ui.apps.shop' in test-automation-qa.yaml is a value: an app is a section with 'url' and,"
                + " optionally, 'secrets'", e.getMessage());
    }

    @Test
    void aMisspelledKeyOfAnAppFailsNamingItAndItsFile() {
        UiConfigException e = assertThrows(UiConfigException.class,
                () -> load("ui:\n  apps:\n    shop:\n      urll: http://shop\n", Map.of()));

        assertTrue(e.getMessage().startsWith("Unknown key 'ui.apps.shop.urll' in test-automation-qa.yaml"),
                e.getMessage());
    }

    private static String apps(String shop) {
        return "ui:\n  apps:\n    shop:\n      url: " + shop + "\n";
    }

    private UiConfig load(String file, Map<String, String> xmlParameters) throws IOException {
        write("test-automation-qa.yaml", file);
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
