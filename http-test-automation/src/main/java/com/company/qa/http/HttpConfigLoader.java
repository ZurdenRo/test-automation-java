package com.company.qa.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Resolves each setting from, in order: the builder, the service config, a default.
 * Remembers where each value came from so that errors can say so. Environment variables are not a source: they hold
 * secrets only (docs/adr/0001 at the repository root).
 */
final class HttpConfigLoader {

    static final String BASE_URL = "base.url";
    static final String CONNECT_TIMEOUT_MS = "connect.timeout.ms";
    static final String RESPONSE_TIMEOUT_MS = "response.timeout.ms";
    static final String MAX_CONNECTIONS = "max.connections";
    static final String FOLLOW_REDIRECTS = "follow.redirects";
    private static final String HEADER_PREFIX = "header.";

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_RESPONSE_TIMEOUT_MS = 30_000;
    private static final int DEFAULT_MAX_CONNECTIONS = 20;

    private final String resource;
    private final Map<String, String> overrides;
    private final Properties file;

    private HttpConfigLoader(String resource, Map<String, String> overrides, Properties file) {
        this.resource = resource;
        this.overrides = overrides;
        this.file = file;
    }

    /**
     * @param resource  classpath resource name, or null when there is no config file
     * @param overrides settings given directly to the builder, by key
     */
    static HttpConfig load(String resource, Map<String, String> overrides, Headers builderHeaders) {
        Properties file = resource == null ? new Properties() : read(resource);
        HttpConfigLoader loader = new HttpConfigLoader(resource, overrides, file);

        Setting baseUrl = loader.find(BASE_URL).orElseThrow(() ->
                new HttpTransportException("Missing '" + BASE_URL + "' (blank values count as missing): looked in "
                        + loader.places(BASE_URL)));
        int connectTimeout = loader.positiveInt(CONNECT_TIMEOUT_MS, DEFAULT_CONNECT_TIMEOUT_MS);
        int responseTimeout = loader.positiveInt(RESPONSE_TIMEOUT_MS, DEFAULT_RESPONSE_TIMEOUT_MS);
        int maxConnections = loader.positiveInt(MAX_CONNECTIONS, DEFAULT_MAX_CONNECTIONS);
        boolean followRedirects = loader.booleanValue(FOLLOW_REDIRECTS, true);

        return new HttpConfig(baseUrl.value, connectTimeout, responseTimeout, maxConnections, followRedirects,
                loader.fileHeaders().overriddenBy(builderHeaders));
    }

    private Optional<Setting> find(String key) {
        String fromBuilder = overrides.get(key);
        if (isPresent(fromBuilder)) {
            return Optional.of(new Setting(fromBuilder, "the builder"));
        }
        String fromFile = file.getProperty(key);
        if (isPresent(fromFile)) {
            return Optional.of(new Setting(fromFile.trim(), "service config '" + resource + "'"));
        }
        return Optional.empty();
    }

    private int positiveInt(String key, int fallback) {
        Optional<Setting> found = find(key);
        if (!found.isPresent()) {
            return fallback;
        }
        Setting setting = found.get();
        try {
            int value = Integer.parseInt(setting.value.trim());
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // reported below
        }
        throw invalid(setting, key, "a positive whole number");
    }

    private boolean booleanValue(String key, boolean fallback) {
        Optional<Setting> found = find(key);
        if (!found.isPresent()) {
            return fallback;
        }
        Setting setting = found.get();
        String value = setting.value.trim().toLowerCase(Locale.ROOT);
        if (value.equals("true") || value.equals("false")) {
            return Boolean.parseBoolean(value);
        }
        throw invalid(setting, key, "true or false");
    }

    /** A blank value is treated as absent, so {@code key=} never hides a real value. */
    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static HttpTransportException invalid(Setting setting, String key, String expected) {
        return new HttpTransportException("Invalid value '" + setting.value + "' for '" + key
                + "' (from " + setting.source + "): expected " + expected);
    }

    private Headers fileHeaders() {
        Headers headers = Headers.empty();
        List<String> names = new ArrayList<>(file.stringPropertyNames());
        names.sort(String::compareTo);
        for (String name : names) {
            if (name.startsWith(HEADER_PREFIX) && name.length() > HEADER_PREFIX.length()) {
                headers = headers.and(name.substring(HEADER_PREFIX.length()), file.getProperty(name).trim());
            }
        }
        return headers;
    }

    private String places(String key) {
        return resource == null ? "the builder" : "the builder, service config '" + resource + "'";
    }



    private static Properties read(String resource) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = HttpConfigLoader.class.getClassLoader();
        }
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new HttpTransportException("Service config '" + resource + "' not found on the classpath");
            }
            Properties properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return properties;
        } catch (IOException e) {
            throw new HttpTransportException("Could not read service config '" + resource + "': " + e.getMessage(), e);
        }
    }

    private static final class Setting {
        final String value;
        final String source;

        Setting(String value, String source) {
            this.value = value;
            this.source = source;
        }
    }
}
