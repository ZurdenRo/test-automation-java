package com.company.qa.http;

import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestParametersTest {

    @Test
    void queryParamsAreEncodedSoSpecialCharactersSurviveTheTrip() throws Exception {
        try (TestServer server = echoingServer();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {

            HttpResponse response = transport.get(HttpRequest.to("/echo")
                    .query("name", "Jose Pérez").query("q", "a&b=c"));

            assertEquals("name=Jose Pérez;q=a&b=c", decodedQueryOf(response));
        }
    }

    @Test
    void requestHeadersAreSentAndWinOverTheTransportDefaults() throws Exception {
        try (TestServer server = echoingServer();
             HttpTransport transport = new HttpTransportBuilder()
                     .baseUrl(server.baseUrl())
                     .defaultHeaders(Headers.of("Accept", "text/plain").and("X-Env", "staging"))
                     .build()) {

            HttpResponse response = transport.get(HttpRequest.to("/echo").header("accept", "application/json"));

            assertEquals(List.of("application/json"), response.headers().all("X-Seen-Accept"));
            assertEquals(List.of("staging"), response.headers().all("X-Seen-Env"));
        }
    }

    @Test
    void aRequestHeaderWithSeveralValuesSendsThemAll() throws Exception {
        try (TestServer server = echoingServer();
             HttpTransport transport = new HttpTransportBuilder()
                     .baseUrl(server.baseUrl())
                     .defaultHeaders(Headers.of("X-Token", "default"))
                     .build()) {

            HttpResponse response = transport.get(HttpRequest.to("/echo")
                    .header("X-Token", "t1").header("x-token", "t2"));

            assertEquals(List.of("t1,t2"), response.headers().all("X-Seen-Token"));
        }
    }

    @Test
    void aRequestCarriesItsQueryAndHeadersToTheServer() throws Exception {
        try (TestServer server = echoingServer();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {

            HttpResponse response = transport.get(HttpRequest.to("/echo")
                    .query("name", "Jose Pérez")
                    .header("X-Token", "t1"));

            assertEquals("name=Jose Pérez", decodedQueryOf(response));
            assertEquals("t1", response.headers().first("X-Seen-Token").get());
        }
    }

    @Test
    void prebuiltQueryParamsAndHeadersCombineWithSingleOnesInOrder() throws Exception {
        try (TestServer server = echoingServer();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {

            HttpResponse response = transport.get(HttpRequest.to("/echo")
                    .query("a", "1")
                    .query(QueryParams.of("b", "2").and("c", "3"))
                    .query("d", "4")
                    .header("X-Token", "t1")
                    .headers(Headers.of("x-token", "t2").and("Accept", "text/plain")));

            assertEquals("a=1;b=2;c=3;d=4", decodedQueryOf(response));
            assertEquals("t1,t2", response.headers().first("X-Seen-Token").get());
            assertEquals("text/plain", response.headers().first("X-Seen-Accept").get());
        }
    }

    @Test
    void addingToARequestLeavesTheOriginalAsItWasSoItCanBeReused() throws Exception {
        try (TestServer server = echoingServer();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {

            HttpRequest base = HttpRequest.to("/echo").query("page", "1").header("X-Token", "t1");
            HttpResponse extended = transport.get(base.query("size", "50").header("X-Token", "t2"));
            HttpResponse original = transport.get(base);

            assertEquals("page=1;size=50", decodedQueryOf(extended));
            assertEquals("t1,t2", extended.headers().first("X-Seen-Token").get());
            assertEquals("page=1", decodedQueryOf(original));
            assertEquals("t1", original.headers().first("X-Seen-Token").get());
        }
    }

    @Test
    void responseHeadersAreCaseInsensitiveAndKeepRepeatedValues() throws Exception {
        try (TestServer server = new TestServer()
                .on("/h", ex -> {
                    ex.getResponseHeaders().add("Content-Type", "text/plain");
                    ex.getResponseHeaders().add("Set-Cookie", "a=1");
                    ex.getResponseHeaders().add("Set-Cookie", "b=2");
                    TestServer.respond(ex, 200, "ok");
                }).start();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {

            Headers headers = transport.get("/h").headers();

            assertEquals("text/plain", headers.first("content-type").get());
            assertEquals(List.of("a=1", "b=2"), headers.all("SET-COOKIE"));
            assertEquals(true, headers.contains("CONTENT-TYPE"));
            assertEquals(false, headers.contains("X-Absent"));
            assertEquals(true, headers.first("X-Absent").isEmpty());
        }
    }

    private static TestServer echoingServer() {
        return new TestServer().on("/echo", RequestParametersTest::echo).start();
    }

    /** Sends the query it received back in the body, and the headers it saw back as X-Seen-*. */
    private static void echo(HttpExchange ex) throws IOException {
        String query = ex.getRequestURI().getRawQuery();
        ex.getResponseHeaders().add("X-Seen-Accept", String.join(",", ex.getRequestHeaders().getOrDefault("Accept", List.of())));
        ex.getResponseHeaders().add("X-Seen-Env", String.join(",", ex.getRequestHeaders().getOrDefault("X-Env", List.of())));
        ex.getResponseHeaders().add("X-Seen-Token", String.join(",", ex.getRequestHeaders().getOrDefault("X-Token", List.of())));
        TestServer.respond(ex, 200, query == null ? "" : query);
    }

    /** Decodes the raw query the server saw, as name=value pairs joined by ';'. */
    private static String decodedQueryOf(HttpResponse response) {
        StringBuilder out = new StringBuilder();
        for (String pair : response.body().split("&")) {
            if (out.length() > 0) {
                out.append(';');
            }
            out.append(URLDecoder.decode(pair, StandardCharsets.UTF_8));
        }
        return out.toString();
    }
}
