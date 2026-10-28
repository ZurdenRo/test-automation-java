package com.company.qa.mobile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {

    private static final String MINIMAL = "mobile:\n  appium-server-url: http://localhost:4723\n  android:\n"
            + "    app: /apps/ApiDemos-debug.apk\n    devices: [emulator-5554]\n";

    @TempDir
    Path classpathDir;

    private final ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();

    @AfterEach
    void restoreClassLoader() {
        Thread.currentThread().setContextClassLoader(originalLoader);
    }

    @Test
    void defaultsApplyWhenTheFileSetsOnlyWhatIsRequired() throws Exception {
        MobileConfig config = load(MINIMAL, Map.of());

        assertEquals("qa", config.environment());
        assertEquals(Platform.ANDROID, config.platform());
        assertEquals(10_000, config.waitUntilTimeoutMs());
        assertEquals(600_000, config.deviceWaitTimeoutMs());
        assertEquals(java.nio.file.Path.of("mobile-evidence"), config.evidenceDir());
    }

    @Test
    void theFileSetsEverySetting() throws Exception {
        MobileConfig config = load("mobile:\n  appium-server-url: https://farm.example:443/wd/hub\n  wait-until-timeout-ms: 500\n"
                + "  device-wait-timeout-ms: 7000\n  evidence-dir: out/evidence\n  android:\n"
                + "    app: /apps/shop.apk\n    devices: [ emulator-5554 , emulator-5556 , '' ]\n", Map.of());

        assertEquals("https://farm.example:443/wd/hub", config.appiumServerUrl().toString());
        assertEquals(500, config.waitUntilTimeoutMs());
        assertEquals(7000, config.deviceWaitTimeoutMs());
        assertEquals(java.nio.file.Path.of("out/evidence"), config.evidenceDir());
        assertEquals("/apps/shop.apk", config.app());
        assertNull(config.appPackage());
        assertEquals(List.of("emulator-5554", "emulator-5556"), config.devices());
    }

    @Test
    void theAppCanBeAnAlreadyInstalledPackage() throws Exception {
        MobileConfig config = load("mobile:\n  appium-server-url: http://localhost:4723\n  android:\n"
                + "    app-package: com.jeffprod.todo\n    devices: [emulator-5554]\n", Map.of());

        assertNull(config.app());
        assertEquals("com.jeffprod.todo", config.appPackage());
    }

    @Test
    void theDefaultEnvironmentsFileIsTheQaOne() throws Exception {
        write("test-automation-qa.yaml", MINIMAL);

        assertEquals("qa", MobileConfigLoader.load(Map.of()).environment());
    }

    @Test
    void theSuiteNamesItsEnvironmentAndItsFileFollows() throws Exception {
        write("test-automation-qa.yaml", MINIMAL);
        write("test-automation-staging.yaml", MINIMAL.replace("localhost", "staging-appium"));

        MobileConfig config = MobileConfigLoader.load(Map.of("env", "staging"));

        assertEquals("staging", config.environment());
        assertEquals("http://staging-appium:4723", config.appiumServerUrl().toString());
    }

    @Test
    void thePlatformParameterPicksWhichNestedKeysAreRead() throws Exception {
        MobileConfig config = load(MINIMAL, Map.of("platform", "ANDROID"));

        assertEquals(Platform.ANDROID, config.platform());
        assertEquals("/apps/ApiDemos-debug.apk", config.app());
    }

    @Test
    void theKeysOfAPlatformThatIsNotSupportedYetAreUnknown() {
        MobileConfigException e = assertThrows(MobileConfigException.class,
                () -> load(MINIMAL + "  ios:\n    app: /apps/shop.ipa\n", Map.of()));

        assertTrue(e.getMessage().startsWith("Unknown key 'mobile.ios.app' in test-automation-qa.yaml"),
                e.getMessage());
    }

    @Test
    void theEnvironmentsFileLaysOverTheSharedOne() throws Exception {
        write("test-automation.yaml", MINIMAL + "  wait-until-timeout-ms: 500\n");
        write("test-automation-staging.yaml", "mobile:\n  appium-server-url: http://staging-appium:4723\n"
                + "  android:\n    devices: [emulator-5556]\n");

        MobileConfig config = MobileConfigLoader.load(Map.of("env", "staging"));

        assertEquals("http://staging-appium:4723", config.appiumServerUrl().toString());
        assertEquals(500, config.waitUntilTimeoutMs());
        assertEquals(List.of("emulator-5556"), config.devices());
    }

    @Test
    void iosIsNotSupportedYet() {
        MobileConfigException e = assertThrows(MobileConfigException.class,
                () -> load(MINIMAL, Map.of("platform", "ios")));

        assertEquals("'platform' from <parameter> platform is 'ios': expected android (iOS is not supported yet)",
                e.getMessage());
    }

    @Test
    void aMissingServerUrlNamesTheFileToAddItTo() {
        MobileConfigException e = assertThrows(MobileConfigException.class,
                () -> load("mobile:\n  android:\n    app: /a.apk\n    devices: [emulator-5554]\n", Map.of()));

        assertEquals("Missing 'mobile.appium-server-url' in test-automation-qa.yaml (blank values count as missing)",
                e.getMessage());
    }

    @Test
    void aServerUrlThatIsNotHttpIsRejected() {
        MobileConfigException e = assertThrows(MobileConfigException.class,
                () -> load(MINIMAL.replace("http://localhost:4723", "localhost:4723"), Map.of()));

        assertEquals("'mobile.appium-server-url' in test-automation-qa.yaml is 'localhost:4723': expected an http or"
                + " https URL", e.getMessage());
    }

    @Test
    void anAppIsRequiredAsAnApkOrAnInstalledPackage() {
        MobileConfigException e = assertThrows(MobileConfigException.class,
                () -> load("mobile:\n  appium-server-url: http://localhost:4723\n  android:\n    devices: [emulator-5554]\n", Map.of()));

        assertEquals("Missing 'mobile.android.app' or 'mobile.android.app-package' in test-automation-qa.yaml: the app under"
                + " test is an APK to install or a package already installed (blank values count as missing)",
                e.getMessage());
    }

    @Test
    void anApkAndAnInstalledPackageTogetherAreAmbiguous() {
        MobileConfigException e = assertThrows(MobileConfigException.class,
                () -> load(MINIMAL + "    app-package: com.jeffprod.todo\n", Map.of()));

        assertEquals("Both 'mobile.android.app' and 'mobile.android.app-package' are set in test-automation-qa.yaml: the app is"
                + " either an APK to install or a package already installed", e.getMessage());
    }

    @Test
    void aMissingDeviceListSaysWhereToGetTheSerials() {
        MobileConfigException e = assertThrows(MobileConfigException.class,
                () -> load("mobile:\n  appium-server-url: http://localhost:4723\n  android:\n    app: /a.apk\n", Map.of()));

        assertEquals("Missing 'mobile.android.devices' in test-automation-qa.yaml: the serials `adb devices` lists,"
                + " as a list (blank values count as missing)", e.getMessage());
    }

    @Test
    void aRepeatedSerialIsRejectedBecauseOneDeviceServesOneSession() {
        MobileConfigException e = assertThrows(MobileConfigException.class,
                () -> load(MINIMAL.replace("[emulator-5554]", "[emulator-5554, emulator-5554]"), Map.of()));

        assertEquals("'mobile.android.devices' in test-automation-qa.yaml is 'emulator-5554, emulator-5554': expected"
                + " device serials without repeats", e.getMessage());
    }

    @Test
    void aNonNumericWaitTimeoutSaysWhereItCameFrom() {
        MobileConfigException e = assertThrows(MobileConfigException.class,
                () -> load(MINIMAL + "  wait-until-timeout-ms: soon\n", Map.of()));

        assertEquals("'mobile.wait-until-timeout-ms' in test-automation-qa.yaml is 'soon': expected a positive whole number",
                e.getMessage());
    }

    @Test
    void aZeroWaitTimeoutIsRejected() {
        MobileConfigException e = assertThrows(MobileConfigException.class,
                () -> load(MINIMAL + "  wait-until-timeout-ms: 0\n", Map.of()));

        assertEquals("'mobile.wait-until-timeout-ms' in test-automation-qa.yaml is '0': expected a positive whole number",
                e.getMessage());
    }

    @Test
    void xmlParametersOtherThanEnvAndPlatformAreIgnored() throws Exception {
        MobileConfig config = load(MINIMAL, Map.of("wait-until-timeout-ms", "900", "appium-server-url", "http://xml"));

        assertEquals(10_000, config.waitUntilTimeoutMs());
        assertEquals("http://localhost:4723", config.appiumServerUrl().toString());
    }

    @Test
    void aMissingConfigResourceSaysWhichEnvironmentNeedsIt() {
        Thread.currentThread().setContextClassLoader(classLoader());

        MobileConfigException e = assertThrows(MobileConfigException.class,
                () -> MobileConfigLoader.load(Map.of("env", "prod")));

        assertEquals("Config file 'test-automation-prod.yaml' (for <parameter> env 'prod') not found on the"
                + " classpath", e.getMessage());
    }

    @Test
    void aMissingDefaultConfigResourceSaysHowToNameAnother() {
        Thread.currentThread().setContextClassLoader(classLoader());

        MobileConfigException e = assertThrows(MobileConfigException.class, () -> MobileConfigLoader.load(Map.of()));

        assertEquals("Config file 'test-automation-qa.yaml' (for env 'qa', the default; name another with"
                + " <parameter> env) not found on the classpath", e.getMessage());
    }

    private MobileConfig load(String file, Map<String, String> xmlParameters) throws IOException {
        write("test-automation-qa.yaml", file);
        return MobileConfigLoader.load(xmlParameters);
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
