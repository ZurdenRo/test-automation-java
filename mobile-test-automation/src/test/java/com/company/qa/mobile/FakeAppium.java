package com.company.qa.mobile;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Speaks just enough of the W3C WebDriver protocol, as an Appium server does, to open and close sessions, and
 * records what it was asked. Stands in for a server and a device in the module's unit tests.
 */
final class FakeAppium implements AutoCloseable {

    private final HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> sessionRequests = new CopyOnWriteArrayList<>();
    private final AtomicInteger open = new AtomicInteger();
    private final AtomicInteger maxOpen = new AtomicInteger();
    private final AtomicInteger created = new AtomicInteger();
    private volatile boolean refuseSessions;
    private volatile boolean refuseEvidence;
    private volatile String pageSource = PAGE_SOURCE;

    FakeAppium() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", this::handle);
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Every request as {@code METHOD path}, in the order they arrived. */
    List<String> requests() {
        return requests;
    }

    /** The bodies of the new-session requests: the capabilities the library asked for. */
    List<String> sessionRequests() {
        return sessionRequests;
    }

    int sessionsCreated() {
        return created.get();
    }

    int sessionsOpen() {
        return open.get();
    }

    /** The most sessions that were ever open at once. */
    int maxSessionsOpen() {
        return maxOpen.get();
    }

    /** Makes new-session requests fail, or succeed again, as a server does when a device is not there. */
    void refuseSessions(boolean refuse) {
        refuseSessions = refuse;
    }

    /** The bytes of the screenshot the server returns, a 1x1 PNG. */
    static final byte[] SCREENSHOT = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");
    static final String PAGE_SOURCE = "<hierarchy><node text=\"Save\"/></hierarchy>";
    static final List<String> LOGCAT = List.of("10-24 10:00:00.000 I/Todo(1): started",
            "10-24 10:00:01.000 E/Todo(1): boom");

    /** The page source the server returns from now on. */
    void pageSource(String source) {
        pageSource = source;
    }

    /** Makes the screenshot, page source and log requests fail, as when a session was lost. */
    void refuseEvidence(boolean refuse) {
        refuseEvidence = refuse;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(method + " " + path);
        if (method.equals("POST") && path.equals("/session")) {
            if (refuseSessions) {
                reply(exchange, 500, "{\"value\":{\"error\":\"session not created\","
                        + "\"message\":\"Device emulator-9999 is not connected\\nsecond line\",\"stacktrace\":\"\"}}");
                return;
            }
            sessionRequests.add(body);
            int now = open.incrementAndGet();
            maxOpen.accumulateAndGet(now, Math::max);
            String id = "session-" + created.incrementAndGet();
            reply(exchange, 200, "{\"value\":{\"sessionId\":\"" + id + "\",\"capabilities\":"
                    + "{\"platformName\":\"Android\",\"appium:automationName\":\"UiAutomator2\"}}}");
        } else if (method.equals("DELETE") && path.matches("/session/[^/]+")) {
            open.decrementAndGet();
            reply(exchange, 200, "{\"value\":null}");
        } else if (path.matches("/session/[^/]+/(screenshot|source|se/log)") && refuseEvidence) {
            reply(exchange, 500, "{\"value\":{\"error\":\"invalid session id\",\"message\":\"gone\",\"stacktrace\":\"\"}}");
        } else if (method.equals("GET") && path.endsWith("/screenshot")) {
            reply(exchange, 200, "{\"value\":\"" + java.util.Base64.getEncoder().encodeToString(SCREENSHOT) + "\"}");
        } else if (method.equals("GET") && path.endsWith("/source")) {
            reply(exchange, 200, "{\"value\":\"" + pageSource.replace("\"", "\\\"") + "\"}");
        } else if (method.equals("POST") && path.endsWith("/se/log")) {
            StringBuilder entries = new StringBuilder();
            for (String line : LOGCAT) {
                entries.append(entries.length() == 0 ? "" : ",").append("{\"level\":\"INFO\",\"timestamp\":1,\"message\":\"")
                        .append(line).append("\"}");
            }
            reply(exchange, 200, "{\"value\":[" + entries + "]}");
        } else if (method.equals("POST") && path.endsWith("/element")) {
            reply(exchange, 404, "{\"value\":{\"error\":\"no such element\",\"message\":\"not there\","
                    + "\"stacktrace\":\"\"}}");
        } else {
            reply(exchange, 200, "{\"value\":null}");
        }
    }

    private static void reply(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
