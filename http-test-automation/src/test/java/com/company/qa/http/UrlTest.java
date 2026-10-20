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

    private static TestServer pathEchoingServer() {
        return new TestServer().on("/", ex -> TestServer.respond(ex, 200, ex.getRequestURI().getPath())).start();
    }
}
