package com.company.qa.mobile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Resolves each setting from the config resource, then a default. Two suite parameters pick what is read:
 * {@code env} the resource ({@code mobile-test-<env>.properties}) and {@code platform} the prefix of the keys that
 * belong to one platform. Remembers where each value came from so that errors can say so. Environment variables are
 * not a source: they hold secrets only.
 */
final class MobileConfigLoader {

    static final String APPIUM_SERVER_URL = "appium.server.url";
    static final String WAIT_TIMEOUT_MS = "wait.timeout.ms";
    static final String APP = "app";
    static final String APP_PACKAGE = "app.package";
    static final String DEVICES = "devices";

    static final String ENV = "env";
    static final String DEFAULT_ENV = "qa";
    static final String PLATFORM = "platform";

    private static final Logger LOG = LoggerFactory.getLogger(MobileConfigLoader.class);

    private final String resource;
    private final Map<String, String> env;
    private final Properties file;

    private MobileConfigLoader(String resource, Map<String, String> env, Properties file) {
        this.resource = resource;
        this.env = env;
        this.file = file;
    }

    /**
     * @param xmlParameters the {@code <parameter>}s of the TestNG {@code <test>} running, its suite's included;
     *                      {@code env} among them names the suite's environment, whose config resource is
     *                      {@code mobile-test-<env>.properties}, and {@code platform} its platform
     */
    static MobileConfig load(Map<String, String> xmlParameters) {
        return load(xmlParameters, System.getenv());
    }

    /** @param env where secrets are read from, and nothing else */
    static MobileConfig load(Map<String, String> xmlParameters, Map<String, String> env) {
        String environment = environment(xmlParameters);
        String resource = resource(environment);
        String origin = isPresent(xmlParameters.get(ENV)) ? "for <parameter> " + ENV + " '" + environment + "'"
                : "for env '" + environment + "', the default; name another with <parameter> " + ENV;
        MobileConfigLoader loader = new MobileConfigLoader(resource, env, read(resource, origin));
        Platform platform = platform(xmlParameters);
        String prefix = platform.key() + ".";

        String app = loader.find(prefix + APP).map(s -> s.value).orElse(null);
        String appPackage = loader.find(prefix + APP_PACKAGE).map(s -> s.value).orElse(null);
        if (app == null && appPackage == null) {
            throw new MobileConfigException("Missing '" + prefix + APP + "' or '" + prefix + APP_PACKAGE
                    + "' in " + resource + ": the app under test is an APK to install or a package already"
                    + " installed (blank values count as missing)");
        }
        if (app != null && appPackage != null) {
            throw new MobileConfigException("Both '" + prefix + APP + "' and '" + prefix + APP_PACKAGE
                    + "' are set in " + resource + ": the app is either an APK to install or a package already"
                    + " installed");
        }
        MobileConfig config = new MobileConfig(loader, environment, platform,
                loader.serverUrl(APPIUM_SERVER_URL),
                loader.positiveInt(WAIT_TIMEOUT_MS, 10_000),
                app, appPackage, loader.serials(prefix + DEVICES));
        LOG.debug("Settings from {}: platform={}, appium.server.url={}, wait.timeout.ms={}, app={}, app.package={},"
                        + " devices={}", resource, platform.key(), config.appiumServerUrl(), config.waitTimeoutMs(),
                config.app(), config.appPackage(), config.devices());
        return config;
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

    private static String resource(String environment) {
        return "mobile-test-" + environment + ".properties";
    }

    /**
     * A secret, read from the environment variable that {@code secret.<name>} names in the environment's file, so a
     * test asks for the same name in every environment. The value never appears in a message.
     */
    String secret(String name) {
        String key = "secret." + name;
        String variable = file.getProperty(key);
        if (!isPresent(variable)) {
            throw new MobileConfigException("Missing '" + key + "' in " + resource + ": it names the environment"
                    + " variable that holds secret '" + name + "'");
        }
        String value = env.get(variable.trim());
        if (!isPresent(value)) {
            throw new MobileConfigException("Secret '" + name + "' needs environment variable " + variable.trim()
                    + ", which is not set (blank values count as missing; " + resource + " names it)");
        }
        LOG.debug("Secret '{}' read from environment variable {}", name, variable.trim());
        return value;
    }

    private Optional<Setting> find(String key) {
        String fromFile = file.getProperty(key);
        if (isPresent(fromFile)) {
            return Optional.of(new Setting(fromFile, resource));
        }
        return Optional.empty();
    }

    private Setting require(String key, String hint) {
        return find(key).orElseThrow(() -> new MobileConfigException("Missing '" + key + "' in " + resource
                + hint + " (blank values count as missing)"));
    }

    private URI serverUrl(String key) {
        Setting setting = require(key, "");
        try {
            URI uri = new URI(setting.value);
            if (("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null) {
                return uri;
            }
        } catch (URISyntaxException e) {
            // reported below, like any other value that is not an http or https URL
        }
        throw invalid(key, setting, "an http or https URL");
    }

    private List<String> serials(String key) {
        Setting setting = require(key, ": the serials `adb devices` lists, separated by commas");
        List<String> serials = new ArrayList<>();
        for (String serial : Arrays.asList(setting.value.split(","))) {
            if (!serial.trim().isEmpty()) {
                serials.add(serial.trim());
            }
        }
        if (serials.isEmpty() || new HashSet<>(serials).size() != serials.size()) {
            throw invalid(key, setting, "device serials separated by commas, without repeats");
        }
        return Collections.unmodifiableList(serials);
    }

    private int positiveInt(String key, int fallback) {
        Optional<Setting> setting = find(key);
        if (setting.isEmpty()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(setting.get().value);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException e) {
            // reported below, like zero and negative numbers
        }
        throw invalid(key, setting.get(), "a positive whole number");
    }

    private static MobileConfigException invalid(String key, Setting setting, String expected) {
        return new MobileConfigException("'" + key + "' from " + setting.source + " is '" + setting.value
                + "': expected " + expected);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static Properties read(String resource, String origin) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new MobileConfigException("Config resource '" + resource + "' (" + origin
                        + ") not found on the classpath");
            }
            Properties properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return properties;
        } catch (IOException e) {
            throw new MobileConfigException("Could not read config resource '" + resource + "'", e);
        }
    }

    private static final class Setting {
        final String value;
        final String source;

        Setting(String value, String source) {
            this.value = value.trim();
            this.source = source;
        }
    }
}
