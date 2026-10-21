package com.company.qa.http;

import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriteBodyTest {

    @Test
    void aJsonBodyIsWrittenWithJacksonOutOfTheBoxAndSentAsApplicationJson() throws Exception {
        try (TestServer server = echoingServer();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {

            HttpResponse response = transport.post(HttpRequest.to("/echo").body(RequestBody.json(new Person(42, "Ada"))));

            assertEquals("application/json|{\"id\":42,\"name\":\"Ada\"}", response.body());
        }
    }

    @Test
    void anExplicitCodecWritesTheJsonBodyInsteadOfTheBuiltInOne() throws Exception {
        BodyCodec upperCaseNames = new BodyCodec() {
            @Override
            public <T> T read(String body, Class<T> type) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String write(Object model) {
                return "{\"NAME\":\"" + ((Person) model).name.toUpperCase() + "\"}";
            }
        };
        try (TestServer server = echoingServer();
             HttpTransport transport = new HttpTransportBuilder()
                     .baseUrl(server.baseUrl()).bodyCodec(upperCaseNames).build()) {

            HttpResponse response = transport.put(HttpRequest.to("/echo").body(RequestBody.json(new Person(42, "Ada"))));

            assertEquals("application/json|{\"NAME\":\"ADA\"}", response.body());
        }
    }

    @Test
    void aBodyThatCannotBeWrittenFailsNamingVerbUrlAndTypeWithoutSendingAnything() throws Exception {
        BodyCodec failing = new BodyCodec() {
            @Override
            public <T> T read(String body, Class<T> type) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String write(Object model) {
                throw new IllegalStateException("no serializer found");
            }
        };
        AtomicInteger received = new AtomicInteger();
        try (TestServer server = new TestServer().on("/echo", ex -> {
                    received.incrementAndGet();
                    TestServer.respond(ex, 200, "");
                }).start();
             HttpTransport transport = new HttpTransportBuilder()
                     .baseUrl(server.baseUrl()).bodyCodec(failing).build()) {

            HttpTransportException e = assertThrows(HttpTransportException.class,
                    () -> transport.post(HttpRequest.to("/echo").body(RequestBody.json(new Person(42, "Ada")))));

            String message = e.getMessage();
            assertTrue(message.contains("POST " + server.baseUrl() + "/echo"), message);
            assertTrue(message.contains("Person"), message);
            assertTrue(message.contains("no serializer found"), message);
            assertEquals(0, received.get());
        }
    }

    private static TestServer echoingServer() {
        return new TestServer().on("/echo", WriteBodyTest::echo).start();
    }

    /** Answers with what it received: {@code Content-Type|body}. */
    private static void echo(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        TestServer.respond(ex, 200, ex.getRequestHeaders().getFirst("Content-Type") + "|" + body);
    }

    public static class Person {
        public int id;
        public String name;

        Person(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
