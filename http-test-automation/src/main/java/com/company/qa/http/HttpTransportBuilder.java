package com.company.qa.http;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configures an {@link HttpServiceClient}. A value given here beats an environment variable, which beats the
 * service config, which beats the default. Building fails if the base URL cannot be resolved.
 */
public final class HttpTransportBuilder {

    private final Map<String, String> overrides = new HashMap<>();
    private String resource;
    private String envPrefix;
    private Map<String, String> environment = System.getenv();
    private Headers defaultHeaders = Headers.empty();
    private BodyDeserializer bodyDeserializer = new JacksonBodyDeserializer();

    /** The service config: a {@code .properties} file on the classpath, named by the consumer, for this service client. */
    public HttpTransportBuilder configResource(String resource) {
        this.resource = resource;
        return this;
    }

    /** Prefix of this service client's environment variables. Defaults to {@code QA_HTTP_<RESOURCE NAME>_}. */
    public HttpTransportBuilder envPrefix(String envPrefix) {
        this.envPrefix = envPrefix;
        return this;
    }

    /** The environment to read variables from. Defaults to the process environment. */
    public HttpTransportBuilder environment(Map<String, String> environment) {
        this.environment = environment;
        return this;
    }

    public HttpTransportBuilder baseUrl(String baseUrl) {
        return set(HttpConfigLoader.BASE_URL, baseUrl);
    }

    public HttpTransportBuilder followRedirects(boolean followRedirects) {
        return set(HttpConfigLoader.FOLLOW_REDIRECTS, String.valueOf(followRedirects));
    }

    public HttpTransportBuilder connectTimeoutMs(int millis) {
        return set(HttpConfigLoader.CONNECT_TIMEOUT_MS, String.valueOf(millis));
    }

    public HttpTransportBuilder responseTimeoutMs(int millis) {
        return set(HttpConfigLoader.RESPONSE_TIMEOUT_MS, String.valueOf(millis));
    }

    public HttpTransportBuilder maxConnections(int max) {
        return set(HttpConfigLoader.MAX_CONNECTIONS, String.valueOf(max));
    }

    /** Sent with every request; headers of the same name given to a single request replace these. */
    public HttpTransportBuilder defaultHeaders(Headers defaultHeaders) {
        this.defaultHeaders = defaultHeaders;
        return this;
    }

    /** What {@link HttpResponse#bodyAs} uses. Optional: the default reads JSON with Jackson. */
    public HttpTransportBuilder bodyDeserializer(BodyDeserializer bodyDeserializer) {
        this.bodyDeserializer = Objects.requireNonNull(bodyDeserializer, "bodyDeserializer");
        return this;
    }

    HttpTransport build() {
        return new HttpTransport(
                HttpConfigLoader.load(resource, envPrefix, environment, overrides, defaultHeaders),
                bodyDeserializer);
    }

    private HttpTransportBuilder set(String key, String value) {
        overrides.put(key, value);
        return this;
    }
}
