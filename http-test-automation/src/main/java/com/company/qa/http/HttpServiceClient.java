package com.company.qa.http;

import java.io.Closeable;

/**
 * Base of a consumer's service client. The consumer extends it and exposes only its own endpoints; the
 * {@code get} methods are protected so that tests call {@code client.getUser(42)}, not raw paths.
 * Create one per suite and close it in teardown: it owns a pool of reusable connections.
 */
public abstract class HttpServiceClient implements Closeable {

    private final HttpTransport transport;

    /** Reads the settings from a {@code .properties} resource on the classpath, named by the consumer. */
    protected HttpServiceClient(String configResource) {
        this(new HttpTransportBuilder().configResource(configResource));
    }

    /** For a client that needs more than a config file: a deserializer, an explicit base URL, an environment. */
    protected HttpServiceClient(HttpTransportBuilder builder) {
        this.transport = builder.build();
    }

    protected HttpResponse get(String path) {
        return transport.get(path);
    }

    protected HttpResponse get(String path, QueryParams query) {
        return transport.get(path, query);
    }

    protected HttpResponse get(String path, Headers headers) {
        return transport.get(path, headers);
    }

    protected HttpResponse get(String path, QueryParams query, Headers headers) {
        return transport.get(path, query, headers);
    }

    /** Closes the connection pool. */
    @Override
    public void close() {
        transport.close();
    }
}
