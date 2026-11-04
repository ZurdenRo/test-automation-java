package com.company.qa.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlTest {

    @Test
    void aPathWithoutALeadingSlashStillLandsUnderTheBaseUrl() throws Exception {
        try (TestServer server = pathEchoingServer();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {

            assertEquals("/users", transport.get("users").body());
        }
    }

    @Test
    void baseUrlPrefixAndPathAreJoinedWithExactlyOneSlashWhateverTheCallerWrote() throws Exception {
        try (TestServer server = pathEchoingServer()) {
            String[][] cases = {
                    {"/api", "/v1/users"},
                    {"/api", "v1/users"},
                    {"/api/", "/v1/users"},
                    {"/api/", "v1/users"},
            };
            for (String[] c : cases) {
                try (HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl() + c[0]).build()) {
                    assertEquals("/api/v1/users", transport.get(c[1]).body(), c[0] + " + " + c[1]);
                }
            }
        }
    }

    @Test
    void anIllegalPathRaisesOurExceptionNamingTheUrlNotARawIllegalArgumentException() throws Exception {
        try (TestServer server = pathEchoingServer();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {

            HttpTransportException e = assertThrows(HttpTransportException.class,
                    () -> transport.get("/users/Jose Pérez"));

            assertTrue(e.getMessage().contains(server.baseUrl() + "/users/Jose Pérez"), e.getMessage());
        }
    }

    @Test
    void anIllegalUrlIsNamedByItsBaseAndPathWithTheReasonButNotTheQuery() throws Exception {
        try (TestServer server = pathEchoingServer();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {
            String url = server.baseUrl() + "/users/Jose Pérez";
            String expected = "Invalid URL for GET " + url + ": Illegal character in path at index " + url.indexOf(' ');

            HttpTransportException fromParams = assertThrows(HttpTransportException.class,
                    () -> transport.get(HttpRequest.to("/users/Jose Pérez").query("page", "2")));
            HttpTransportException fromPath = assertThrows(HttpTransportException.class,
                    () -> transport.get("/users/Jose Pérez?page=2"));

            assertEquals(expected, fromParams.getMessage());
            assertEquals(expected, fromPath.getMessage());
        }
    }

    private static TestServer pathEchoingServer() {
        return new TestServer().on("/", ex -> TestServer.respond(ex, 200, ex.getRequestURI().getPath())).start();
    }
}
