package com.company.qa.http;

import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WriteRequestTest {

    @Test
    void postSendsATextBodyWithItsContentType() throws Exception {
        try (TestServer server = echoingServer();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {

            HttpResponse response = transport.post(HttpRequest.to("/echo")
                    .body(RequestBody.text("id,name\n1,Ada", "text/csv")));

            assertEquals("POST|text/csv|id,name\n1,Ada", response.body());
        }
    }

    @Test
    void putPatchAndDeleteReachTheServerWithTheirMethod() throws Exception {
        try (TestServer server = echoingServer();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {

            RequestBody body = RequestBody.text("name=Ada", "text/plain");

            assertEquals("PUT|text/plain|name=Ada", transport.put(HttpRequest.to("/echo").body(body)).body());
            assertEquals("PATCH|text/plain|name=Ada", transport.patch(HttpRequest.to("/echo").body(body)).body());
            assertEquals("DELETE|-|", transport.delete(HttpRequest.to("/echo")).body());
        }
    }

    @Test
    void aBodyOnGetOrDeleteIsRejectedNamingTheVerbAndPath() throws Exception {
        try (TestServer server = echoingServer();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {

            HttpRequest withBody = HttpRequest.to("/api/v1/users").body(RequestBody.text("x", "text/plain"));

            IllegalArgumentException onGet = assertThrows(IllegalArgumentException.class, () -> transport.get(withBody));
            IllegalArgumentException onDelete = assertThrows(IllegalArgumentException.class, () -> transport.delete(withBody));

            assertEquals("GET /api/v1/users does not take a body", onGet.getMessage());
            assertEquals("DELETE /api/v1/users does not take a body", onDelete.getMessage());
        }
    }

    @Test
    void anExplicitContentTypeHeaderWinsOverTheBodysAndIsSentOnce() throws Exception {
        try (TestServer server = new TestServer().on("/types", ex -> TestServer.respond(ex, 200,
                String.join(",", ex.getRequestHeaders().get("Content-Type")))).start();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {

            HttpResponse response = transport.post(HttpRequest.to("/types")
                    .header("content-type", "application/vnd.api+json")
                    .body(RequestBody.text("{}", "text/plain")));

            assertEquals("application/vnd.api+json", response.body());
        }
    }

    @Test
    void postPutAndPatchWithoutABodySendAnEmptyOneAndNoContentType() throws Exception {
        try (TestServer server = echoingServer();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {

            HttpRequest cancel = HttpRequest.to("/echo");

            assertEquals("POST|-|", transport.post(cancel).body());
            assertEquals("PUT|-|", transport.put(cancel).body());
            assertEquals("PATCH|-|", transport.patch(cancel).body());
        }
    }

    @Test
    void aPostCarriesItsQueryAndTheDefaultAndRequestHeadersLikeAGet() throws Exception {
        try (TestServer server = new TestServer().on("/jobs", ex -> TestServer.respond(ex, 200,
                ex.getRequestURI().getRawQuery() + "|" + ex.getRequestHeaders().getFirst("X-Env")
                        + "|" + ex.getRequestHeaders().getFirst("X-Trace"))).start();
             HttpTransport transport = new HttpTransportBuilder()
                     .baseUrl(server.baseUrl()).defaultHeaders(Headers.of("X-Env", "staging")).build()) {

            HttpResponse response = transport.post(HttpRequest.to("/jobs")
                    .query("dryRun", "true").header("X-Trace", "t1"));

            assertEquals("dryRun=true|staging|t1", response.body());
        }
    }

    private static TestServer echoingServer() {
        return new TestServer().on("/echo", WriteRequestTest::echo).start();
    }

    /** Answers with what it received: {@code METHOD|Content-Type|body}; a missing Content-Type shows as "-". */
    private static void echo(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        TestServer.respond(ex, 200, ex.getRequestMethod() + "|" + (contentType == null ? "-" : contentType) + "|" + body);
    }
}
