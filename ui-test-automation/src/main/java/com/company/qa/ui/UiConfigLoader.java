package com.company.qa.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * Resolves each setting from, in order: a {@code <parameter>} of the TestNG {@code <test>} (only for the settings
 * that make sense to vary per {@code <test>}), an environment variable, the config resource, a default.
 * Remembers where each value came from so that errors can say so.
 */
final class UiConfigLoader {

    static final String BASE_URL = "base.url";
    static final String BROWSER = "browser";
    static final String CHANNEL = "channel";
    static final String HEADLESS = "headless";
    static final String TIMEOUT_MS = "timeout.ms";
    static final String VIEWPORT_WIDTH = "viewport.width";
    static final String VIEWPORT_HEIGHT = "viewport.height";
    static final String SLOW_MO_MS = "slow.mo.ms";
    static final String EVIDENCE_DIR = "evidence.dir";
    static final String TRACE = "trace";

    private static final String ENV_PREFIX = "QA_UI_";
    private static final Set<String> XML_SETTINGS = Set.of(BROWSER, CHANNEL, HEADLESS, VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
    private static final List<String> BROWSERS = List.of("chromium", "firefox", "webkit");
    private static final List<String> CHANNELS = List.of("chrome", "msedge");
    private static final List<String> TRACE_MODES = List.of("off", "on-failure");

    private final String resource;
    private final Map<String, String> env;
    private final Map<String, String> xmlParameters;
    private final Properties file;

    private UiConfigLoader(String resource, Map<String, String> env, Map<String, String> xmlParameters,
                           Properties file) {
        this.resource = resource;
        this.env = env;
        this.xmlParameters = xmlParameters;
        this.file = file;
    }

    /** @param xmlParameters the {@code <parameter>}s of the TestNG {@code <test>} running, empty outside one */
    static UiConfig load(String resource, Map<String, String> env, Map<String, String> xmlParameters) {
        UiConfigLoader loader = new UiConfigLoader(resource, env, xmlParameters, read(resource));
        return new UiConfig(loader,
                loader.oneOf(BROWSER, BROWSERS, "chromium", " or "),
                loader.oneOf(CHANNEL, CHANNELS, null, " or "),
                loader.booleanValue(HEADLESS, true),
                loader.positiveInt(TIMEOUT_MS, 30_000),
                loader.positiveInt(VIEWPORT_WIDTH, 1280),
                loader.positiveInt(VIEWPORT_HEIGHT, 720),
                loader.nonNegativeInt(SLOW_MO_MS, 0),
                Path.of(loader.find(EVIDENCE_DIR).map(s -> s.value).orElse("ui-evidence")),
                "on-failure".equals(loader.oneOf(TRACE, TRACE_MODES, "off", " or ")));
    }

    /** The URL of the default app (null name) or of a named one, for the page that needs it. */
    String appUrl(String app, String pageName) {
        String key = app == null ? BASE_URL : "app." + app + ".url";
        return find(key).map(s -> s.value).orElseThrow(() -> new UiConfigException("Missing '" + key + "' for "
                + pageName + " (blank values count as missing): looked in " + places(key)));
    }

    private Optional<Setting> find(String key) {
        if (XML_SETTINGS.contains(key)) {
            String fromXml = xmlParameters.get(key);
            if (isPresent(fromXml)) {
                return Optional.of(new Setting(fromXml, "<parameter> " + key));
            }
        }
        String envName = envName(key);
        String fromEnv = env.get(envName);
        if (isPresent(fromEnv)) {
            return Optional.of(new Setting(fromEnv, "environment variable " + envName));
        }
        String fromFile = file.getProperty(key);
        if (isPresent(fromFile)) {
            return Optional.of(new Setting(fromFile, resource));
        }
        return Optional.empty();
    }

    private String places(String key) {
        return (XML_SETTINGS.contains(key) ? "<parameter> " + key + ", " : "")
                + "environment variable " + envName(key) + ", " + resource;
    }

    private String oneOf(String key, List<String> allowed, String fallback, String lastSeparator) {
        Optional<Setting> setting = find(key);
        if (setting.isEmpty()) {
            return fallback;
        }
        String value = setting.get().value.toLowerCase(Locale.ROOT);
        if (!allowed.contains(value)) {
            String expected = String.join(", ", allowed.subList(0, allowed.size() - 1)) + lastSeparator
                    + allowed.get(allowed.size() - 1);
            throw invalid(key, setting.get(), expected);
        }
        return value;
    }

    private boolean booleanValue(String key, boolean fallback) {
        Optional<Setting> setting = find(key);
        if (setting.isEmpty()) {
            return fallback;
        }
        String value = setting.get().value.toLowerCase(Locale.ROOT);
        if (!value.equals("true") && !value.equals("false")) {
            throw invalid(key, setting.get(), "true or false");
        }
        return Boolean.parseBoolean(value);
    }

    private int positiveInt(String key, int fallback) {
        int value = intValue(key, fallback, "a positive whole number");
        if (value <= 0) {
            throw invalid(key, find(key).get(), "a positive whole number");
        }
        return value;
    }

    private int nonNegativeInt(String key, int fallback) {
        int value = intValue(key, fallback, "zero or a positive whole number");
        if (value < 0) {
            throw invalid(key, find(key).get(), "zero or a positive whole number");
        }
        return value;
    }

    private int intValue(String key, int fallback, String expected) {
        Optional<Setting> setting = find(key);
        if (setting.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(setting.get().value);
        } catch (NumberFormatException e) {
            throw invalid(key, setting.get(), expected);
        }
    }

    private static UiConfigException invalid(String key, Setting setting, String expected) {
        return new UiConfigException("'" + key + "' from " + setting.source + " is '" + setting.value
                + "': expected " + expected);
    }

    private static String envName(String key) {
        return ENV_PREFIX + key.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static Properties read(String resource) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new UiConfigException("Config resource '" + resource + "' not found on the classpath");
            }
            Properties properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return properties;
        } catch (IOException e) {
            throw new UiConfigException("Could not read config resource '" + resource + "'", e);
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
