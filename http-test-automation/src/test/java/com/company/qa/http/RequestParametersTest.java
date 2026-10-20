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

            HttpResponse response = transport.get("/echo",
                    QueryParams.of("name", "Jose Pérez").and("q", "a&b=c"));

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

            HttpResponse response = transport.get("/echo", Headers.of("accept", "application/json"));

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

            HttpResponse response = transport.get("/echo", Headers.of("X-Token", "t1").and("x-token", "t2"));

            assertEquals(List.of("t1,t2"), response.headers().all("X-Seen-Token"));
        }
    }

    @Test
    void allFourGetOverloadsReachTheServer() throws Exception {
        try (TestServer server = echoingServer();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {

            QueryParams query = QueryParams.of("k", "v");
            Headers headers = Headers.of("X-Token", "t1");

            assertEquals(200, transport.get("/echo").status());
            assertEquals("k=v", decodedQueryOf(transport.get("/echo", query)));
            assertEquals("t1", transport.get("/echo", headers).headers().first("X-Seen-Token").get());

            HttpResponse both = transport.get("/echo", query, headers);
            assertEquals("k=v", decodedQueryOf(both));
            assertEquals("t1", both.headers().first("X-Seen-Token").get());
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
