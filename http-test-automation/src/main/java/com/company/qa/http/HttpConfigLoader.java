package com.company.qa.http;

import com.company.qa.config.Config;
import com.company.qa.config.ConfigException;
import com.company.qa.config.Section;
import com.company.qa.config.Setting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Resolves each setting from, in order: the builder, the service's section of the config
 * ({@code services.<name>} in {@code test-automation-<env>.yaml} over {@code test-automation.yaml}), a default.
 * Remembers where each value came from so that errors can say so. Environment variables are not a source: they hold
 * secrets only (docs/adr/0001 at the repository root).
 */
final class HttpConfigLoader {

    static final String BASE_URL = "base-url";
    static final String CONNECT_TIMEOUT_MS = "connect-timeout-ms";
    static final String RESPONSE_TIMEOUT_MS = "response-timeout-ms";
    static final String MAX_CONNECTIONS = "max-connections";
    static final String FOLLOW_REDIRECTS = "follow-redirects";
    private static final String HEADERS = "headers";
    private static final String SECRETS = "secrets";
    private static final List<String> KEYS = List.of(BASE_URL, CONNECT_TIMEOUT_MS, RESPONSE_TIMEOUT_MS,
            MAX_CONNECTIONS, FOLLOW_REDIRECTS);
    private static final List<String> OPEN_PREFIXES = List.of(HEADERS, SECRETS);

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_RESPONSE_TIMEOUT_MS = 30_000;
    private static final int DEFAULT_MAX_CONNECTIONS = 20;

    private static final Logger LOG = LoggerFactory.getLogger(HttpConfigLoader.class);

    private final Config config;
    private final Section service;
    private final Map<String, String> overrides;

    private HttpConfigLoader(Config config, Section service, Map<String, String> overrides) {
        this.config = config;
        this.service = service;
        this.overrides = overrides;
    }

    /**
     * @param serviceName the service's name under {@code services}, or null when there is no config
     * @param environment the environment whose files are read; ignored without a service
     * @param overrides   settings given directly to the builder, by key
     * @param env         where secrets are read from, and nothing else
     */
    static HttpConfig load(String serviceName, String environment, Map<String, String> overrides,
                           Headers builderHeaders, Map<String, String> env) {
        Config config = serviceName == null ? null : translated(() ->
                Config.load(environment, "needed by service client '" + serviceName + "'"));
        Section service = serviceName == null ? null : config.section("services." + serviceName);
        if (service != null) {
            translated(() -> {
                service.rejectUnknown(KEYS, OPEN_PREFIXES);
                return null;
            });
        }
        HttpConfigLoader loader = new HttpConfigLoader(config, service, overrides);

        Setting baseUrl = loader.find(BASE_URL).orElseThrow(() ->
                new HttpTransportException("Missing '" + loader.fullKey(BASE_URL) + "' (blank values count as"
                        + " missing): looked in " + loader.places()));
        int connectTimeout = loader.positiveInt(CONNECT_TIMEOUT_MS, DEFAULT_CONNECT_TIMEOUT_MS);
        int responseTimeout = loader.positiveInt(RESPONSE_TIMEOUT_MS, DEFAULT_RESPONSE_TIMEOUT_MS);
        int maxConnections = loader.positiveInt(MAX_CONNECTIONS, DEFAULT_MAX_CONNECTIONS);
        boolean followRedirects = translated(() -> loader.find(FOLLOW_REDIRECTS).map(Setting::bool).orElse(true));

        Headers headers = loader.fileHeaders().overriddenBy(builderHeaders);

        LOG.info("Service client on {} ({} from {})", baseUrl.value(), BASE_URL, baseUrl.source());
        // Header names only: a value may be a token.
        LOG.debug("Settings: connect-timeout-ms={}, response-timeout-ms={}, max-connections={}, follow-redirects={},"
                + " default headers {}", connectTimeout, responseTimeout, maxConnections, followRedirects,
                headers.names());
        return new HttpConfig(baseUrl.value(), connectTimeout, responseTimeout, maxConnections, followRedirects,
                headers, new Secrets(service, env));
    }

    private Optional<Setting> find(String key) {
        String fromBuilder = overrides.get(key);
        if (fromBuilder != null && !fromBuilder.trim().isEmpty()) {
            return Optional.of(Setting.of(key, fromBuilder, "the builder"));
        }
        return service == null ? Optional.empty() : translated(() -> service.find(key));
    }

    private int positiveInt(String key, int fallback) {
        return translated(() -> find(key).map(Setting::positiveInt).orElse(fallback));
    }

    private String fullKey(String key) {
        return service == null ? key : service.path() + "." + key;
    }

    private Headers fileHeaders() {
        Headers headers = Headers.empty();
        if (service == null) {
            return headers;
        }
        for (String name : service.section(HEADERS).names()) {
            headers = headers.and(name, translated(() -> service.require(HEADERS + "." + name, "").value()));
        }
        return headers;
    }

    private String places() {
        return config == null ? "the builder" : "the builder, " + String.join(" or ", config.files());
    }

    private static <T> T translated(Supplier<T> action) {
        try {
            return action.get();
        } catch (ConfigException e) {
            throw new HttpTransportException(e.getMessage(), e);
        }
    }
}
