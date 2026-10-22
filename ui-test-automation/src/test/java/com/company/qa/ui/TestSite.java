package com.company.qa.ui;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A local web app on a free port, so tests drive a real browser without the internet. Each page shows its own
 * name and the path it was served at, so a test can tell where the browser landed.
 */
final class TestSite implements AutoCloseable {

    private final String name;
    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, String> bodies = new ConcurrentHashMap<>();

    TestSite(String name) {
        this.name = name;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            String body = bodies.getOrDefault(path, "");
            String html = "<!doctype html><html><head><title>" + name + "</title></head><body>"
                    + "<h1 id='app'>" + name + "</h1><p id='path'>" + path + "</p>" + body + "</body></html>";
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    /** Extra HTML for the page served at {@code path}. */
    TestSite page(String path, String body) {
        bodies.put(path, body);
        return this;
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    String name() {
        return name;
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
