package com.company.qa.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GetRequestTest {

    @Test
    void getReturnsStatusAndBodyFromTheServer() throws Exception {
        try (TestServer server = new TestServer()
                .on("/users/42", ex -> TestServer.respond(ex, 200, "hello"))
                .start();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {

            HttpResponse response = transport.get("/users/42");

            assertEquals(200, response.status());
            assertEquals("hello", response.body());
        }
    }
}
