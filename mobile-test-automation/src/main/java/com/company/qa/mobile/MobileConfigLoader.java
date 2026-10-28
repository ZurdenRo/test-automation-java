package com.company.qa.mobile;

import com.company.qa.config.Config;
import com.company.qa.config.ConfigException;
import com.company.qa.config.Section;
import com.company.qa.config.Setting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Resolves each setting from the {@code mobile} section of the config, then a default. The config is
 * {@code test-automation-<env>.yaml} over {@code test-automation.yaml} (root docs/adr/0005). Two suite parameters pick
 * what is read: {@code env} the files and {@code platform} the nested section ({@code mobile.android}) whose keys
 * belong to one platform. Remembers where each value came from so that errors can say so. Environment variables are
 * not a source: they hold secrets only.
 */
final class MobileConfigLoader {

    static final String APPIUM_SERVER_URL = "appium-server-url";
    static final String WAIT_UNTIL_TIMEOUT_MS = "wait-until-timeout-ms";
    static final String DEVICE_WAIT_TIMEOUT_MS = "device-wait-timeout-ms";
    static final String EVIDENCE_DIR = "evidence-dir";
    static final String APP = "app";
    static final String APP_PACKAGE = "app-package";
    static final String DEVICES = "devices";
    private static final String SECRETS = "secrets";

    static final String ENV = "env";
    static final String DEFAULT_ENV = Config.DEFAULT_ENV;
    static final String PLATFORM = "platform";

    private static final Logger LOG = LoggerFactory.getLogger(MobileConfigLoader.class);

    private final Config config;
    private final Section mobile;
    private final Map<String, String> env;

    private MobileConfigLoader(Config config, Section mobile, Map<String, String> env) {
        this.config = config;
        this.mobile = mobile;
        this.env = env;
    }

    /**
     * @param xmlParameters the {@code <parameter>}s of the TestNG {@code <test>} running, its suite's included;
     *                      {@code env} among them names the suite's environment, whose settings are in
     *                      {@code test-automation-<env>.yaml}, and {@code platform} its platform
     */
    static MobileConfig load(Map<String, String> xmlParameters) {
        return load(xmlParameters, System.getenv());
    }

    /** @param env where secrets are read from, and nothing else */
    static MobileConfig load(Map<String, String> xmlParameters, Map<String, String> env) {
        String environment = environment(xmlParameters);
        String origin = isPresent(xmlParameters.get(ENV)) ? "for <parameter> " + ENV + " '" + environment + "'"
                : "for env '" + environment + "', the default; name another with <parameter> " + ENV;
        Config config = translated(() -> Config.load(environment, origin));
        Section mobile = config.section("mobile");
        MobileConfigLoader loader = new MobileConfigLoader(config, mobile, env);
        Platform platform = platform(xmlParameters);
        String prefix = platform.key() + ".";
        translated(() -> {
            mobile.rejectUnknown(knownKeys(), List.of(SECRETS));
            return null;
        });

        Optional<Setting> app = translated(() -> mobile.find(prefix + APP));
        Optional<Setting> appPackage = translated(() -> mobile.find(prefix + APP_PACKAGE));
        String appKey = "mobile." + prefix + APP;
        String packageKey = "mobile." + prefix + APP_PACKAGE;
        if (!app.isPresent() && !appPackage.isPresent()) {
            throw new MobileConfigException("Missing '" + appKey + "' or '" + packageKey + "' in " + loader.files()
                    + ": the app under test is an APK to install or a package already installed (blank values count"
                    + " as missing)");
        }
        if (app.isPresent() && appPackage.isPresent()) {
            throw new MobileConfigException("Both '" + appKey + "' and '" + packageKey + "' are set in "
                    + loader.files() + ": the app is either an APK to install or a package already installed");
        }
        MobileConfig result = translated(() -> new MobileConfig(loader, environment, platform,
                loader.serverUrl(APPIUM_SERVER_URL),
                loader.positiveInt(WAIT_UNTIL_TIMEOUT_MS, 10_000),
                loader.positiveInt(DEVICE_WAIT_TIMEOUT_MS, 600_000),
                app.map(Setting::value).orElse(null), appPackage.map(Setting::value).orElse(null),
                loader.serials(prefix + DEVICES),
                Path.of(mobile.find(EVIDENCE_DIR).map(Setting::value).orElse("mobile-evidence"))));
        LOG.debug("Settings from {}: platform={}, appium-server-url={}, wait-until-timeout-ms={},"
                        + " device-wait-timeout-ms={}, app={}, app-package={}, devices={}, evidence-dir={}",
                config.files(), platform.key(), result.appiumServerUrl(), result.waitUntilTimeoutMs(),
                result.deviceWaitTimeoutMs(), result.app(), result.appPackage(), result.devices(),
                result.evidenceDir());
        return result;
    }

    /** Logs the environment, its files and the platform a TestNG {@code <test>} runs on, once; a platform not supported fails here. */
    static void logEnvironment(String testName, Map<String, String> xmlParameters) {
        String environment = environment(xmlParameters);
        String files;
        try {
            files = String.join(" + ", Config.load(environment, null).files());
        } catch (ConfigException e) {
            files = Config.environmentFile(environment);
        }
        LOG.info("<test> '{}' runs on env '{}' ({}), platform {}", testName, environment, files,
                platform(xmlParameters).key());
    }

    private static List<String> knownKeys() {
        List<String> keys = new ArrayList<>(List.of(APPIUM_SERVER_URL, WAIT_UNTIL_TIMEOUT_MS, DEVICE_WAIT_TIMEOUT_MS,
                EVIDENCE_DIR));
        for (Platform platform : Platform.values()) {
            for (String key : List.of(APP, APP_PACKAGE, DEVICES)) {
                keys.add(platform.key() + "." + key);
            }
        }
        return keys;
    }

    private static String environment(Map<String, String> xmlParameters) {
        String named = xmlParameters.get(ENV);
        return isPresent(named) ? named.trim() : DEFAULT_ENV;
    }

    private static Platform platform(Map<String, String> xmlParameters) {
        String named = xmlParameters.get(PLATFORM);
        if (!isPresent(named)) {
            return Platform.ANDROID;
        }
        Platform platform = Platform.ofKey(named.trim().toLowerCase(Locale.ROOT));
        if (platform == null) {
            throw new MobileConfigException("'" + PLATFORM + "' from <parameter> " + PLATFORM + " is '"
                    + named.trim() + "': expected android (iOS is not supported yet)");
        }
        return platform;
    }

    /**
     * A secret, read from the environment variable that {@code mobile.secrets.<name>} names in the environment's
     * file, so a test asks for the same name in every environment. The value never appears in a message.
     */
    String secret(String name) {
        String value = translated(() -> mobile.secret(name, env));
        LOG.debug("Secret '{}' read from environment variable {}", name,
                mobile.find(SECRETS + "." + name).map(Setting::value).orElse("?"));
        return value;
    }

    private String files() {
        return String.join(" or ", config.files());
    }

    private URI serverUrl(String key) {
        Setting setting = mobile.require(key, "");
        try {
            URI uri = new URI(setting.value());
            if (("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null) {
                return uri;
            }
        } catch (URISyntaxException e) {
            // reported below, like any other value that is not an http or https URL
        }
        throw setting.invalid("an http or https URL");
    }

    private List<String> serials(String key) {
        String full = "mobile." + key;
        List<Setting> items = mobile.list(key).orElseThrow(() -> new MobileConfigException("Missing '" + full
                + "' in " + files() + ": the serials `adb devices` lists, as a list (blank values count as missing)"));
        List<String> serials = new ArrayList<>();
        for (Setting item : items) {
            if (!item.value().isEmpty()) {
                serials.add(item.value());
            }
        }
        if (serials.isEmpty()) {
            throw new MobileConfigException("'" + full + "' in " + files() + " is an empty list: expected the"
                    + " serials `adb devices` lists");
        }
        if (new HashSet<>(serials).size() != serials.size()) {
            throw new MobileConfigException("'" + full + "' in " + items.get(0).source() + " is '"
                    + String.join(", ", serials) + "': expected device serials without repeats");
        }
        return Collections.unmodifiableList(serials);
    }

    private int positiveInt(String key, int fallback) {
        return mobile.find(key).map(Setting::positiveInt).orElse(fallback);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static <T> T translated(Supplier<T> action) {
        try {
            return action.get();
        } catch (ConfigException e) {
            throw new MobileConfigException(e.getMessage(), e);
        }
    }
}
