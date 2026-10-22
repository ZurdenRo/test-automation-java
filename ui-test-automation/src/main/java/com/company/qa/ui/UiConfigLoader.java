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
 * that make sense to vary per {@code <test>}), the config resource, a default. Remembers where each value came
 * from so that errors can say so. Environment variables are not a source: they hold secrets only.
 */
final class UiConfigLoader {

    static final String BROWSER = "browser";
    static final String CHANNEL = "channel";
    static final String HEADLESS = "headless";
    static final String TIMEOUT_MS = "timeout.ms";
    static final String VIEWPORT_WIDTH = "viewport.width";
    static final String VIEWPORT_HEIGHT = "viewport.height";
    static final String SLOW_MO_MS = "slow.mo.ms";
    static final String EVIDENCE_DIR = "evidence.dir";
    static final String TRACE = "trace";

    static final String CONFIG = "ui.config";
    static final String DEFAULT_CONFIG_RESOURCE = "ui-test-qa.properties";
    private static final Set<String> XML_SETTINGS = Set.of(BROWSER, CHANNEL, HEADLESS, VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
    private static final List<String> BROWSERS = List.of("chromium", "firefox", "webkit");
    private static final List<String> CHANNELS = List.of("chrome", "msedge");
    private static final List<String> TRACE_MODES = List.of("off", "on-failure");

    private final String resource;
    private final Map<String, String> xmlParameters;
    private final Map<String, String> env;
    private final Properties file;

    private UiConfigLoader(String resource, Map<String, String> xmlParameters, Map<String, String> env,
                           Properties file) {
        this.resource = resource;
        this.xmlParameters = xmlParameters;
        this.env = env;
        this.file = file;
    }

    /**
     * @param xmlParameters the {@code <parameter>}s of the TestNG {@code <test>} running, its suite's included;
     *                      {@code ui.config} among them names the config resource of the suite's environment
     */
    static UiConfig load(Map<String, String> xmlParameters) {
        return load(xmlParameters, System.getenv());
    }

    /** @param env where secrets are read from, and nothing else */
    static UiConfig load(Map<String, String> xmlParameters, Map<String, String> env) {
        String named = xmlParameters.get(CONFIG);
        String resource = isPresent(named) ? named.trim() : DEFAULT_CONFIG_RESOURCE;
        String origin = isPresent(named) ? "from <parameter> " + CONFIG
                : "the default; name another with <parameter> " + CONFIG;
        UiConfigLoader loader = new UiConfigLoader(resource, xmlParameters, env, read(resource, origin));
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

    /** The base URL of the named app ({@code app.<name>.url}), for the page that needs it. */
    String appUrl(String app, String pageName) {
        String key = "app." + app + ".url";
        return find(key).map(s -> s.value).orElseThrow(() -> new UiConfigException("Missing '" + key + "' for "
                + pageName + " in " + resource + " (blank values count as missing)"));
    }

    /**
     * A secret, read from the environment variable that {@code secret.<name>} names in the environment's file, so a
     * test asks for the same name in every environment. The value never appears in a message.
     */
    String secret(String name) {
        String key = "secret." + name;
        String variable = file.getProperty(key);
        if (!isPresent(variable)) {
            throw new UiConfigException("Missing '" + key + "' in " + resource + ": it names the environment"
                    + " variable that holds secret '" + name + "'");
        }
        String value = env.get(variable.trim());
        if (!isPresent(value)) {
            throw new UiConfigException("Secret '" + name + "' needs environment variable " + variable.trim()
                    + ", which is not set (blank values count as missing; " + resource + " names it)");
        }
        return value;
    }

    private Optional<Setting> find(String key) {
        if (XML_SETTINGS.contains(key)) {
            String fromXml = xmlParameters.get(key);
            if (isPresent(fromXml)) {
                return Optional.of(new Setting(fromXml, "<parameter> " + key));
            }
        }
        String fromFile = file.getProperty(key);
        if (isPresent(fromFile)) {
            return Optional.of(new Setting(fromFile, resource));
        }
        return Optional.empty();
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

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static Properties read(String resource, String origin) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new UiConfigException("Config resource '" + resource + "' (" + origin
                        + ") not found on the classpath");
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
