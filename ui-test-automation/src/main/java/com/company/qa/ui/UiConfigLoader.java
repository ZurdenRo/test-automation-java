package com.company.qa.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Resolves each setting from, in order: an environment variable, the config resource, a default.
 * Remembers where each value came from so that errors can say so.
 */
final class UiConfigLoader {

    static final String BASE_URL = "base.url";
    private static final String ENV_PREFIX = "QA_UI_";

    private final String resource;
    private final Map<String, String> env;
    private final Properties file;

    private UiConfigLoader(String resource, Map<String, String> env, Properties file) {
        this.resource = resource;
        this.env = env;
        this.file = file;
    }

    static UiConfig load(String resource, Map<String, String> env) {
        UiConfigLoader loader = new UiConfigLoader(resource, env, read(resource));
        return new UiConfig(loader);
    }

    /** The URL of the default app (null name) or of a named one, for the page that needs it. */
    String appUrl(String app, String pageName) {
        String key = app == null ? BASE_URL : "app." + app + ".url";
        return find(key).orElseThrow(() -> new UiConfigException("Missing '" + key + "' for " + pageName
                + " (blank values count as missing): looked in " + places(key)));
    }

    private Optional<String> find(String key) {
        String fromEnv = env.get(envName(key));
        if (isPresent(fromEnv)) {
            return Optional.of(fromEnv.trim());
        }
        String fromFile = file.getProperty(key);
        if (isPresent(fromFile)) {
            return Optional.of(fromFile.trim());
        }
        return Optional.empty();
    }

    private String places(String key) {
        return "environment variable " + envName(key) + ", " + resource;
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
}
