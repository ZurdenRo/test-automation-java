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
            resource("payments.properties", "base.url=" + server.baseUrl() + "\nheader.X-Env=staging\n");

            try (HttpTransport transport = new HttpTransportBuilder()
                    .configResource("payments.properties").build()) {
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
                    .configResource("payments.properties")
                    .defaultHeaders(Headers.of("x-env", "prod")).build()) {
                assertEquals("prod", transport.get("/who").headers().first("X-Seen-Env").get());
            }
        }
    }




    @Test
    void theBuilderBeatsTheFile() throws Exception {
        try (TestServer server = okServer("real")) {
            resource("payments.properties", "base.url=" + DEAD_URL + "\n");

            try (HttpTransport transport = new HttpTransportBuilder()
                    .configResource("payments.properties").baseUrl(server.baseUrl()).build()) {
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
                    .configResource("svc.properties").build()) {
                assertEquals(302, transport.get("/old").status());
            }
        }
    }

    @Test
    void missingResourceFailsAtBuildNamingTheResource() {
        HttpTransportException e = assertThrows(HttpTransportException.class, () ->
                new HttpTransportBuilder().configResource("absent.properties").build());

        assertTrue(e.getMessage().contains("absent.properties"), e.getMessage());
    }

    @Test
    void missingBaseUrlFailsAtBuildNamingTheKeyAndEveryPlaceItWasLookedFor() throws Exception {
        resource("payments.properties", "follow.redirects=true\n");

        HttpTransportException e = assertThrows(HttpTransportException.class, () ->
                new HttpTransportBuilder().configResource("payments.properties").build());

        assertEquals("Missing 'base.url' (blank values count as missing): looked in the builder,"
                + " service config 'payments.properties'", e.getMessage());
    }

    @Test
    void missingBaseUrlFailsAtBuildEvenWithNoConfigResourceAtAll() {
        HttpTransportException e = assertThrows(HttpTransportException.class, () ->
                new HttpTransportBuilder().build());

        assertTrue(e.getMessage().contains("base.url"), e.getMessage());
    }

    @Test
    void blankBaseUrlInTheFileFailsAtBuildWithOurErrorNotAtTheFirstRequest() throws Exception {
        resource("payments.properties", "base.url=   \n");

        HttpTransportException e = assertThrows(HttpTransportException.class, () ->
                new HttpTransportBuilder().configResource("payments.properties").build());

        assertTrue(e.getMessage().contains("base.url"), e.getMessage());
        assertTrue(e.getMessage().contains("payments.properties"), e.getMessage());
    }


    @Test
    void blankBaseUrlGivenToTheBuilderFailsAtBuildWithOurError() {
        HttpTransportException e = assertThrows(HttpTransportException.class, () ->
                new HttpTransportBuilder().baseUrl(" ").build());

        assertTrue(e.getMessage().contains("base.url"), e.getMessage());
    }

    @Test
    void invalidNumberFailsAtBuildSayingWhereTheValueCameFrom() throws Exception {
        resource("payments.properties", "base.url=" + DEAD_URL + "\nconnect.timeout.ms=soon\n");

        HttpTransportException e = assertThrows(HttpTransportException.class, () ->
                new HttpTransportBuilder().configResource("payments.properties").build());

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
