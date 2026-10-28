package com.company.qa.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {

    private static final String DEAD_URL = "http://127.0.0.1:1";

    @TempDir
    Path classpathDir;

    private final ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();

    @AfterEach
    void restoreClassLoader() {
        Thread.currentThread().setContextClassLoader(originalLoader);
    }

    @Test
    void baseUrlAndDefaultHeadersComeFromTheConfigResource() throws Exception {
        try (TestServer server = okServer("payments")) {
            resource("test-automation-qa.yaml", service("payments", "base-url: " + server.baseUrl(), "headers:",
                    "  X-Env: staging"));

            try (HttpTransport transport = new HttpTransportBuilder()
                    .service("payments", "qa").build()) {
                HttpResponse response = transport.get("/who");

                assertEquals("payments", response.body());
                assertEquals("staging", response.headers().first("X-Seen-Env").get());
            }
        }
    }

    @Test
    void builderDefaultHeadersBeatTheHeadersInTheFile() throws Exception {
        try (TestServer server = okServer("payments")) {
            resource("test-automation-qa.yaml", service("payments", "base-url: " + server.baseUrl(), "headers:",
                    "  X-Env: staging"));

            try (HttpTransport transport = new HttpTransportBuilder()
                    .service("payments", "qa")
                    .defaultHeaders(Headers.of("x-env", "prod")).build()) {
                assertEquals("prod", transport.get("/who").headers().first("X-Seen-Env").get());
            }
        }
    }




    @Test
    void theBuilderBeatsTheFile() throws Exception {
        try (TestServer server = okServer("real")) {
            resource("test-automation-qa.yaml", service("payments", "base-url: " + DEAD_URL));

            try (HttpTransport transport = new HttpTransportBuilder()
                    .service("payments", "qa").baseUrl(server.baseUrl()).build()) {
                assertEquals("real", transport.get("/who").body());
            }
        }
    }

    @Test
    void followRedirectsCanBeSwitchedOffFromTheFile() throws Exception {
        try (TestServer server = new TestServer()
                .on("/old", ex -> {
                    ex.getResponseHeaders().add("Location", "/new");
                    TestServer.respond(ex, 302, "");
                }).start()) {
            resource("test-automation-qa.yaml", service("svc", "base-url: " + server.baseUrl(), "follow-redirects: false"));

            try (HttpTransport transport = new HttpTransportBuilder()
                    .service("svc", "qa").build()) {
                assertEquals(302, transport.get("/old").status());
            }
        }
    }

    @Test
    void aMissingEnvironmentFileFailsAtBuildNamingItAndTheClientThatNeedsIt() {
        HttpTransportException e = assertThrows(HttpTransportException.class, () ->
                new HttpTransportBuilder().service("payments", "absent").build());

        assertEquals("Config file 'test-automation-absent.yaml' (needed by service client 'payments') not found on"
                + " the classpath", e.getMessage());
    }

    @Test
    void missingBaseUrlFailsAtBuildNamingTheKeyAndEveryPlaceItWasLookedFor() throws Exception {
        resource("test-automation-qa.yaml", service("payments", "follow-redirects: true"));

        HttpTransportException e = assertThrows(HttpTransportException.class, () ->
                new HttpTransportBuilder().service("payments", "qa").build());

        assertEquals("Missing 'services.payments.base-url' (blank values count as missing): looked in the builder,"
                + " test-automation-qa.yaml", e.getMessage());
    }

    @Test
    void theEnvironmentsFileLaysOverTheSharedOneForTheService() throws Exception {
        try (TestServer server = okServer("payments")) {
            resource("test-automation.yaml", service("payments", "base-url: " + DEAD_URL, "headers:", "  X-Env: shared"));
            resource("test-automation-qa.yaml", service("payments", "base-url: " + server.baseUrl()));

            try (HttpTransport transport = new HttpTransportBuilder().service("payments", "qa").build()) {
                HttpResponse response = transport.get("/who");

                assertEquals("payments", response.body());
                assertEquals("shared", response.headers().first("X-Seen-Env").get());
            }
        }
    }

    @Test
    void aMisspelledSettingFailsAtBuildNamingItAndItsFile() throws Exception {
        resource("test-automation-qa.yaml", service("payments", "base-url: " + DEAD_URL, "connect-timeout: 5"));

        HttpTransportException e = assertThrows(HttpTransportException.class, () ->
                new HttpTransportBuilder().service("payments", "qa").build());

        assertTrue(e.getMessage().startsWith("Unknown key 'services.payments.connect-timeout' in"
                + " test-automation-qa.yaml"), e.getMessage());
    }

    @Test
    void missingBaseUrlFailsAtBuildEvenWithNoConfigResourceAtAll() {
        HttpTransportException e = assertThrows(HttpTransportException.class, () ->
                new HttpTransportBuilder().build());

        assertTrue(e.getMessage().contains("base-url"), e.getMessage());
    }

    @Test
    void blankBaseUrlInTheFileFailsAtBuildWithOurErrorNotAtTheFirstRequest() throws Exception {
        resource("test-automation-qa.yaml", service("payments", "base-url: '   '"));

        HttpTransportException e = assertThrows(HttpTransportException.class, () ->
                new HttpTransportBuilder().service("payments", "qa").build());

        assertTrue(e.getMessage().contains("base-url"), e.getMessage());
        assertTrue(e.getMessage().contains("test-automation-qa.yaml"), e.getMessage());
    }


    @Test
    void blankBaseUrlGivenToTheBuilderFailsAtBuildWithOurError() {
        HttpTransportException e = assertThrows(HttpTransportException.class, () ->
                new HttpTransportBuilder().baseUrl(" ").build());

        assertTrue(e.getMessage().contains("base-url"), e.getMessage());
    }

    @Test
    void invalidNumberFailsAtBuildSayingWhereTheValueCameFrom() throws Exception {
        resource("test-automation-qa.yaml", service("payments", "base-url: " + DEAD_URL, "connect-timeout-ms: soon"));

        HttpTransportException e = assertThrows(HttpTransportException.class, () ->
                new HttpTransportBuilder().service("payments", "qa").build());

        assertTrue(e.getMessage().contains("connect-timeout-ms"), e.getMessage());
        assertTrue(e.getMessage().contains("soon"), e.getMessage());
        assertTrue(e.getMessage().contains("test-automation-qa.yaml"), e.getMessage());
    }

    private static TestServer okServer(String name) {
        return new TestServer().on("/who", ex -> {
            ex.getResponseHeaders().add("X-Seen-Env", String.join(",", ex.getRequestHeaders().getOrDefault("X-Env", java.util.List.of())));
            TestServer.respond(ex, 200, name);
        }).start();
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
