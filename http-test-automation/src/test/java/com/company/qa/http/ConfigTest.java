package com.company.qa.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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
            resource("payments.properties", "base.url=" + server.baseUrl() + "\nheader.X-Env=staging\n");

            try (HttpTransport transport = new HttpTransportBuilder()
                    .configResource("payments.properties").environment(Map.of()).build()) {
                HttpResponse response = transport.get("/who");

                assertEquals("payments", response.body());
                assertEquals("staging", response.headers().first("X-Seen-Env").get());
            }
        }
    }

    @Test
    void builderDefaultHeadersBeatTheHeadersInTheFile() throws Exception {
        try (TestServer server = okServer("payments")) {
            resource("payments.properties", "base.url=" + server.baseUrl() + "\nheader.X-Env=staging\n");

            try (HttpTransport transport = new HttpTransportBuilder()
                    .configResource("payments.properties").environment(Map.of())
                    .defaultHeaders(Headers.of("x-env", "prod")).build()) {
                assertEquals("prod", transport.get("/who").headers().first("X-Seen-Env").get());
            }
        }
    }

    @Test
    void environmentBeatsTheFileAndTheBuilderBeatsTheEnvironment() throws Exception {
        try (TestServer server = okServer("real")) {
            resource("payments.properties", "base.url=" + DEAD_URL + "\n");
            Map<String, String> env = Map.of("QA_HTTP_PAYMENTS_BASE_URL", server.baseUrl());

            try (HttpTransport fromEnv = new HttpTransportBuilder()
                    .configResource("payments.properties").environment(env).build()) {
                assertEquals("real", fromEnv.get("/who").body());
            }

            Map<String, String> wrongEnv = Map.of("QA_HTTP_PAYMENTS_BASE_URL", DEAD_URL);
            try (HttpTransport fromBuilder = new HttpTransportBuilder()
                    .configResource("payments.properties").environment(wrongEnv)
                    .baseUrl(server.baseUrl()).build()) {
                assertEquals("real", fromBuilder.get("/who").body());
            }
        }
    }

    @Test
    void twoServicesInOneProjectDoNotShareEnvironmentVariables() throws Exception {
        try (TestServer payments = okServer("payments"); TestServer users = okServer("users")) {
            resource("payments.properties", "base.url=" + DEAD_URL + "\n");
            resource("users.properties", "base.url=" + users.baseUrl() + "\n");
            Map<String, String> env = Map.of("QA_HTTP_PAYMENTS_BASE_URL", payments.baseUrl());

            try (HttpTransport p = new HttpTransportBuilder().configResource("payments.properties").environment(env).build();
                 HttpTransport u = new HttpTransportBuilder().configResource("users.properties").environment(env).build()) {
                assertEquals("payments", p.get("/who").body());
                assertEquals("users", u.get("/who").body());
            }
        }
    }

    @Test
    void theEnvironmentPrefixCanBeSetExplicitly() throws Exception {
        try (TestServer server = okServer("real")) {
            resource("payments.properties", "base.url=" + DEAD_URL + "\n");

            try (HttpTransport transport = new HttpTransportBuilder()
                    .configResource("payments.properties").envPrefix("MY_APP_")
                    .environment(Map.of("MY_APP_BASE_URL", server.baseUrl())).build()) {
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
            resource("svc.properties", "base.url=" + server.baseUrl() + "\nfollow.redirects=false\n");

            try (HttpTransport transport = new HttpTransportBuilder()
                    .configResource("svc.properties").environment(Map.of()).build()) {
                assertEquals(302, transport.get("/old").status());
            }
        }
    }

    @Test
    void missingResourceFailsAtBuildNamingTheResource() {
        HttpTransportException e = assertThrows(HttpTransportException.class, () ->
                new HttpTransportBuilder().configResource("absent.properties").environment(Map.of()).build());

        assertTrue(e.getMessage().contains("absent.properties"), e.getMessage());
    }

    @Test
    void missingBaseUrlFailsAtBuildNamingTheKeyAndEveryPlaceItWasLookedFor() throws Exception {
        resource("payments.properties", "follow.redirects=true\n");

        HttpTransportException e = assertThrows(HttpTransportException.class, () ->
                new HttpTransportBuilder().configResource("payments.properties").environment(Map.of()).build());

        assertTrue(e.getMessage().contains("base.url"), e.getMessage());
        assertTrue(e.getMessage().contains("QA_HTTP_PAYMENTS_BASE_URL"), e.getMessage());
        assertTrue(e.getMessage().contains("payments.properties"), e.getMessage());
    }

    @Test
    void missingBaseUrlFailsAtBuildEvenWithNoConfigResourceAtAll() {
        HttpTransportException e = assertThrows(HttpTransportException.class, () ->
                new HttpTransportBuilder().environment(Map.of()).build());

        assertTrue(e.getMessage().contains("base.url"), e.getMessage());
    }

    @Test
    void blankBaseUrlInTheFileFailsAtBuildWithOurErrorNotAtTheFirstRequest() throws Exception {
        resource("payments.properties", "base.url=   \n");

        HttpTransportException e = assertThrows(HttpTransportException.class, () ->
                new HttpTransportBuilder().configResource("payments.properties").environment(Map.of()).build());

        assertTrue(e.getMessage().contains("base.url"), e.getMessage());
        assertTrue(e.getMessage().contains("payments.properties"), e.getMessage());
    }

    @Test
    void blankEnvironmentVariableDoesNotHideTheValueInTheFile() throws Exception {
        try (TestServer server = okServer("from-file")) {
            resource("payments.properties", "base.url=" + server.baseUrl() + "\n");

            try (HttpTransport transport = new HttpTransportBuilder()
                    .configResource("payments.properties")
                    .environment(Map.of("QA_HTTP_PAYMENTS_BASE_URL", "")).build()) {
                assertEquals("from-file", transport.get("/who").body());
            }
        }
    }

    @Test
    void blankBaseUrlGivenToTheBuilderFailsAtBuildWithOurError() {
        HttpTransportException e = assertThrows(HttpTransportException.class, () ->
                new HttpTransportBuilder().baseUrl(" ").environment(Map.of()).build());

        assertTrue(e.getMessage().contains("base.url"), e.getMessage());
    }

    @Test
    void invalidNumberFailsAtBuildSayingWhereTheValueCameFrom() throws Exception {
        resource("payments.properties", "base.url=" + DEAD_URL + "\nconnect.timeout.ms=soon\n");

        HttpTransportException e = assertThrows(HttpTransportException.class, () ->
                new HttpTransportBuilder().configResource("payments.properties").environment(Map.of()).build());

        assertTrue(e.getMessage().contains("connect.timeout.ms"), e.getMessage());
        assertTrue(e.getMessage().contains("soon"), e.getMessage());
        assertTrue(e.getMessage().contains("payments.properties"), e.getMessage());
    }

    private static TestServer okServer(String name) {
        return new TestServer().on("/who", ex -> {
            ex.getResponseHeaders().add("X-Seen-Env", String.join(",", ex.getRequestHeaders().getOrDefault("X-Env", java.util.List.of())));
            TestServer.respond(ex, 200, name);
        }).start();
    }

    private void resource(String name, String content) throws IOException {
        Files.writeString(classpathDir.resolve(name), content);
        Thread.currentThread().setContextClassLoader(
                new URLClassLoader(new URL[]{classpathDir.toUri().toURL()}, originalLoader));
    }
}
