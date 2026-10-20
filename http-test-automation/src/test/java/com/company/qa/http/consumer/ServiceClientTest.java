package com.company.qa.http.consumer;

import com.company.qa.http.HttpRequest;
import com.company.qa.http.HttpResponse;
import com.company.qa.http.HttpServiceClient;
import com.company.qa.http.HttpTransportBuilder;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Written the way a consumer project writes it: from another package, extending the library's class. */
class ServiceClientTest {

    /** The consumer's client: it only exposes its own endpoints. */
    static class UsersClient extends HttpServiceClient {

        UsersClient(HttpTransportBuilder builder) {
            super(builder);
        }

        public HttpResponse getUser(int id) {
            return get("/api/v1/users/" + id);
        }

        public HttpResponse findUsers(String name) {
            return get(HttpRequest.to("/api/v1/users").query("name", name).header("X-Trace", "t1"));
        }
    }

    @Test
    void aSubclassReachesItsServiceThroughTheInheritedGetAndClosesWithTryWithResources() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/users", ex -> {
            String answer = ex.getRequestURI().getPath() + "?" + ex.getRequestURI().getRawQuery()
                    + "|" + ex.getRequestHeaders().getFirst("X-Trace");
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            try (UsersClient client = new UsersClient(
                    new HttpTransportBuilder().baseUrl(baseUrl).environment(Map.of()))) {

                assertEquals("/api/v1/users/42?null|null", client.getUser(42).body());
                assertEquals("/api/v1/users?name=Ada|t1", client.findUsers("Ada").body());
            }
        } finally {
            server.stop(0);
        }
    }
}
