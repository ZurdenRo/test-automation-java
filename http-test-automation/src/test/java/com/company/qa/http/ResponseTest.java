package com.company.qa.http;

import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResponseTest {

    @Test
    void notFoundAndServerErrorComeBackAsResponsesNotExceptions() throws Exception {
        try (TestServer server = new TestServer()
                .on("/missing", ex -> TestServer.respond(ex, 404, "nope"))
                .on("/boom", ex -> TestServer.respond(ex, 500, "kaput"))
                .start();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {

            HttpResponse missing = transport.get("/missing");
            HttpResponse boom = transport.get("/boom");

            assertEquals(404, missing.status());
            assertEquals("nope", missing.body());
            assertEquals(500, boom.status());
            assertEquals("kaput", boom.body());
        }
    }

    @Test
    void redirectIsFollowedByDefaultAndFinalUriIsExposed() throws Exception {
        try (TestServer server = redirectingServer();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {

            HttpResponse response = transport.get("/old");

            assertEquals(200, response.status());
            assertEquals("moved here", response.body());
            assertEquals(URI.create(server.baseUrl() + "/new"), response.finalUri());
        }
    }

    @Test
    void aChainOfRedirectsIsFollowedToTheEndAndFinalUriIsTheLastHop() throws Exception {
        try (TestServer server = new TestServer()
                .on("/first", ex -> redirect(ex, "/second"))
                .on("/second", ex -> redirect(ex, "/third"))
                .on("/third", ex -> TestServer.respond(ex, 200, "arrived"))
                .start();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {

            HttpResponse response = transport.get("/first");

            assertEquals(200, response.status());
            assertEquals("arrived", response.body());
            assertEquals(URI.create(server.baseUrl() + "/third"), response.finalUri());
        }
    }

    @Test
    void redirectCanBeTurnedOffSoTheTestSeesTheRedirectItself() throws Exception {
        try (TestServer server = redirectingServer();
             HttpTransport transport = new HttpTransportBuilder()
                     .baseUrl(server.baseUrl())
                     .followRedirects(false)
                     .build()) {

            HttpResponse response = transport.get("/old");

            assertEquals(302, response.status());
            assertEquals(URI.create(server.baseUrl() + "/old"), response.finalUri());
        }
    }

    private static TestServer redirectingServer() {
        return new TestServer()
                .on("/old", ex -> redirect(ex, "/new"))
                .on("/new", ex -> TestServer.respond(ex, 200, "moved here"))
                .start();
    }

    private static void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().add("Location", location);
        TestServer.respond(ex, 302, "");
    }
}
