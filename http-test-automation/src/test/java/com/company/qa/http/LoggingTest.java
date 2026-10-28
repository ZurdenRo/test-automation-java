package com.company.qa.http;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingTest {

    @TempDir
    Path classpathDir;

    private final Logger logger = (Logger) LoggerFactory.getLogger("com.company.qa.http");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
    private Level originalLevel;

    @BeforeEach
    void capture() {
        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void restore() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
        Thread.currentThread().setContextClassLoader(originalLoader);
    }

    @Test
    void aServiceClientLogsItsBaseUrlAndWhereItCameFromAtInfo() throws IOException {
        resource("test-automation-qa.yaml", service("payments", "base-url: http://127.0.0.1:1"));

        new HttpTransportBuilder().service("payments", "qa").build().close();

        assertTrue(messages(Level.INFO).contains(
                "Service client on http://127.0.0.1:1 (base-url from test-automation-qa.yaml)"),
                () -> messages(Level.INFO).toString());
    }

    @Test
    void eachRequestIsLoggedAtDebugWithItsStatusButNotItsQueryValues() {
        try (TestServer server = new TestServer().on("/search", ex -> TestServer.respond(ex, 200, "")).start();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {

            transport.get(HttpRequest.to("/search").query("key", "s3cr3t").query("page", "2"));

            String prefix = "GET " + server.baseUrl() + "/search?key=…&page=… -> 200 in ";
            assertTrue(messages(Level.DEBUG).stream().anyMatch(m -> m.startsWith(prefix)),
                    () -> messages(Level.DEBUG).toString());
            assertNothingLogged("s3cr3t");
        }
    }

    @Test
    void defaultHeadersAreLoggedByNameNeverByValue() {
        new HttpTransportBuilder().baseUrl("http://127.0.0.1:1")
                .defaultHeaders(Headers.of("Authorization", "Bearer t0ken")).build().close();

        assertTrue(messages(Level.DEBUG).stream().anyMatch(m -> m.endsWith("default headers [Authorization]")),
                () -> messages(Level.DEBUG).toString());
        assertNothingLogged("t0ken");
    }

    @Test
    void aSecretIsLoggedByItsVariableNeverByItsValue() throws IOException {
        resource("test-automation-qa.yaml", service("payments", "base-url: http://127.0.0.1:1", "secrets:",
                "  api-token: PAYMENTS_QA_TOKEN"));

        try (HttpTransport transport = new HttpTransportBuilder().service("payments", "qa")
                .secretsFrom(Map.of("PAYMENTS_QA_TOKEN", "qa-token")).build()) {
            transport.secret("api-token");
        }

        assertTrue(messages(Level.DEBUG).contains("Secret 'api-token' read from environment variable PAYMENTS_QA_TOKEN"),
                () -> messages(Level.DEBUG).toString());
        assertNothingLogged("qa-token");
    }

    @Test
    void queryValuesAreElidedWhereverTheQueryCameFrom() {
        assertEquals("http://h/p", HttpTransport.withoutQueryValues("http://h/p"));
        assertEquals("http://h/p?a=…&flag&b=…", HttpTransport.withoutQueryValues("http://h/p?a=1&flag&b=x%3Dy"));
    }

    private void assertNothingLogged(String text) {
        assertFalse(appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains(text)),
                "'" + text + "' was logged");
    }

    private List<String> messages(Level level) {
        return appender.list.stream()
                .filter(e -> e.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());
    }

    /** The file of the qa environment holding one service's settings, one per line. */
    private static String service(String name, String... lines) {
        StringBuilder yaml = new StringBuilder("services:\n  " + name + ":\n");
        for (String line : lines) {
            yaml.append("    ").append(line).append('\n');
        }
        return yaml.toString();
    }

    private void resource(String name, String content) throws IOException {
        Files.writeString(classpathDir.resolve(name), content);
        Thread.currentThread().setContextClassLoader(
                new URLClassLoader(new URL[]{classpathDir.toUri().toURL()}, originalLoader));
    }
}
