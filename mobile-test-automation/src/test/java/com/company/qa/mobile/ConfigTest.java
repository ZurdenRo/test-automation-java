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

class ConfigTest {

    private static final String MINIMAL = "appium.server.url=http://localhost:4723\n"
            + "android.app=/apps/ApiDemos-debug.apk\nandroid.devices=emulator-5554\n";

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
        assertEquals(10_000, config.waitTimeoutMs());
    }

    @Test
    void theFileSetsEverySetting() throws Exception {
        MobileConfig config = load("appium.server.url=https://farm.example:443/wd/hub\nwait.timeout.ms=500\n"
                + "android.app=/apps/shop.apk\nandroid.devices= emulator-5554 , emulator-5556 ,\n", Map.of());

        assertEquals("https://farm.example:443/wd/hub", config.appiumServerUrl().toString());
        assertEquals(500, config.waitTimeoutMs());
        assertEquals("/apps/shop.apk", config.app());
        assertNull(config.appPackage());
        assertEquals(List.of("emulator-5554", "emulator-5556"), config.devices());
    }

    @Test
    void theAppCanBeAnAlreadyInstalledPackage() throws Exception {
        MobileConfig config = load("appium.server.url=http://localhost:4723\n"
                + "android.app.package=com.jeffprod.todo\nandroid.devices=emulator-5554\n", Map.of());

        assertNull(config.app());
        assertEquals("com.jeffprod.todo", config.appPackage());
    }

    @Test
    void theDefaultFileIsTheQaOne() throws Exception {
        write("mobile-test-qa.properties", MINIMAL);

        assertEquals("qa", MobileConfigLoader.load(Map.of()).environment());
    }

    @Test
    void theSuiteNamesItsEnvironmentAndItsFileFollows() throws Exception {
        write("mobile-test-qa.properties", MINIMAL);
        write("mobile-test-staging.properties", MINIMAL.replace("localhost", "staging-appium"));

        MobileConfig config = MobileConfigLoader.load(Map.of("env", "staging"));

        assertEquals("staging", config.environment());
        assertEquals("http://staging-appium:4723", config.appiumServerUrl().toString());
    }

    @Test
    void thePlatformParameterPicksWhichKeysAreRead() throws Exception {
        MobileConfig config = load(MINIMAL + "ios.app=/apps/shop.ipa\nios.devices=iPhone 15\n",
                Map.of("platform", "ANDROID"));

        assertEquals(Platform.ANDROID, config.platform());
        assertEquals("/apps/ApiDemos-debug.apk", config.app());
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
                () -> load("android.app=/a.apk\nandroid.devices=emulator-5554\n", Map.of()));

        assertEquals("Missing 'appium.server.url' in mobile-test-qa.properties (blank values count as missing)",
                e.getMessage());
    }

    @Test
    void aServerUrlThatIsNotHttpIsRejected() {
        MobileConfigException e = assertThrows(MobileConfigException.class,
                () -> load(MINIMAL.replace("http://localhost:4723", "localhost:4723"), Map.of()));

        assertEquals("'appium.server.url' from mobile-test-qa.properties is 'localhost:4723': expected an http or"
                + " https URL", e.getMessage());
    }

    @Test
    void anAppIsRequiredAsAnApkOrAnInstalledPackage() {
        MobileConfigException e = assertThrows(MobileConfigException.class,
                () -> load("appium.server.url=http://localhost:4723\nandroid.devices=emulator-5554\n", Map.of()));

        assertEquals("Missing 'android.app' or 'android.app.package' in mobile-test-qa.properties: the app under"
                + " test is an APK to install or a package already installed (blank values count as missing)",
                e.getMessage());
    }

    @Test
    void anApkAndAnInstalledPackageTogetherAreAmbiguous() {
        MobileConfigException e = assertThrows(MobileConfigException.class,
                () -> load(MINIMAL + "android.app.package=com.jeffprod.todo\n", Map.of()));

        assertEquals("Both 'android.app' and 'android.app.package' are set in mobile-test-qa.properties: the app is"
                + " either an APK to install or a package already installed", e.getMessage());
    }

    @Test
    void aMissingDeviceListSaysWhereToGetTheSerials() {
        MobileConfigException e = assertThrows(MobileConfigException.class,
                () -> load("appium.server.url=http://localhost:4723\nandroid.app=/a.apk\n", Map.of()));

        assertEquals("Missing 'android.devices' in mobile-test-qa.properties: the serials `adb devices` lists,"
                + " separated by commas (blank values count as missing)", e.getMessage());
    }

    @Test
    void aRepeatedSerialIsRejectedBecauseOneDeviceServesOneSession() {
        MobileConfigException e = assertThrows(MobileConfigException.class,
                () -> load(MINIMAL.replace("emulator-5554", "emulator-5554,emulator-5554"), Map.of()));

        assertEquals("'android.devices' from mobile-test-qa.properties is 'emulator-5554,emulator-5554': expected"
                + " device serials separated by commas, without repeats", e.getMessage());
    }

    @Test
    void aNonNumericWaitTimeoutSaysWhereItCameFrom() {
        MobileConfigException e = assertThrows(MobileConfigException.class,
                () -> load(MINIMAL + "wait.timeout.ms=soon\n", Map.of()));

        assertEquals("'wait.timeout.ms' from mobile-test-qa.properties is 'soon': expected a positive whole number",
                e.getMessage());
    }

    @Test
    void aZeroWaitTimeoutIsRejected() {
        MobileConfigException e = assertThrows(MobileConfigException.class,
                () -> load(MINIMAL + "wait.timeout.ms=0\n", Map.of()));

        assertEquals("'wait.timeout.ms' from mobile-test-qa.properties is '0': expected a positive whole number",
                e.getMessage());
    }

    @Test
    void xmlParametersOtherThanEnvAndPlatformAreIgnored() throws Exception {
        MobileConfig config = load(MINIMAL, Map.of("wait.timeout.ms", "900", "appium.server.url", "http://xml"));

        assertEquals(10_000, config.waitTimeoutMs());
        assertEquals("http://localhost:4723", config.appiumServerUrl().toString());
    }

    @Test
    void aMissingConfigResourceSaysWhichEnvironmentNeedsIt() {
        Thread.currentThread().setContextClassLoader(classLoader());

        MobileConfigException e = assertThrows(MobileConfigException.class,
                () -> MobileConfigLoader.load(Map.of("env", "prod")));

        assertEquals("Config resource 'mobile-test-prod.properties' (for <parameter> env 'prod') not found on the"
                + " classpath", e.getMessage());
    }

    @Test
    void aMissingDefaultConfigResourceSaysHowToNameAnother() {
        Thread.currentThread().setContextClassLoader(classLoader());

        MobileConfigException e = assertThrows(MobileConfigException.class, () -> MobileConfigLoader.load(Map.of()));

        assertEquals("Config resource 'mobile-test-qa.properties' (for env 'qa', the default; name another with"
                + " <parameter> env) not found on the classpath", e.getMessage());
    }

    private MobileConfig load(String file, Map<String, String> xmlParameters) throws IOException {
        write("mobile-test-qa.properties", file);
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
