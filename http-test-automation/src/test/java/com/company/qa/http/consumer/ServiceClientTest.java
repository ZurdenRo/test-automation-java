package com.company.qa.http.consumer;

import com.company.qa.http.HttpRequest;
import com.company.qa.http.HttpResponse;
import com.company.qa.http.HttpServiceClient;
import com.company.qa.http.HttpTransportBuilder;
import com.company.qa.http.RequestBody;
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

        public HttpResponse createUser(String csv) {
            return post(HttpRequest.to("/api/v1/users").body(RequestBody.text(csv, "text/csv")));
        }

        public HttpResponse replaceUser(int id, String name) {
            return put(HttpRequest.to("/api/v1/users/" + id).body(RequestBody.text(name, "text/plain")));
        }

        public HttpResponse renameUser(int id, String name) {
            return patch(HttpRequest.to("/api/v1/users/" + id).body(RequestBody.text(name, "text/plain")));
        }

        public HttpResponse deleteUser(int id) {
            return delete("/api/v1/users/" + id);
        }

        public HttpResponse deleteUserTraced(int id) {
            return delete(HttpRequest.to("/api/v1/users/" + id).header("X-Trace", "t2"));
        }

        public HttpResponse reindex() {
            return post("/api/v1/users/reindex");
        }

        public HttpResponse touch(int id) {
            return put("/api/v1/users/" + id + "/touch");
        }

        public HttpResponse refresh(int id) {
            return patch("/api/v1/users/" + id + "/refresh");
        }
    }

    @Test
    void aSubclassReachesItsServiceThroughEveryInheritedVerbAndClosesWithTryWithResources() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/users", ex -> {
            String answer = ex.getRequestMethod() + " " + ex.getRequestURI().getPath()
                    + "?" + ex.getRequestURI().getRawQuery()
                    + "|" + ex.getRequestHeaders().getFirst("X-Trace")
                    + "|" + new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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

                assertEquals("GET /api/v1/users/42?null|null|", client.getUser(42).body());
                assertEquals("GET /api/v1/users?name=Ada|t1|", client.findUsers("Ada").body());
                assertEquals("POST /api/v1/users?null|null|1,Ada", client.createUser("1,Ada").body());
                assertEquals("PUT /api/v1/users/42?null|null|Ada", client.replaceUser(42, "Ada").body());
                assertEquals("PATCH /api/v1/users/42?null|null|Ada", client.renameUser(42, "Ada").body());
                assertEquals("DELETE /api/v1/users/42?null|null|", client.deleteUser(42).body());
                assertEquals("DELETE /api/v1/users/42?null|t2|", client.deleteUserTraced(42).body());
                assertEquals("POST /api/v1/users/reindex?null|null|", client.reindex().body());
                assertEquals("PUT /api/v1/users/42/touch?null|null|", client.touch(42).body());
                assertEquals("PATCH /api/v1/users/42/refresh?null|null|", client.refresh(42).body());
            }
        } finally {
            server.stop(0);
        }
    }
}
