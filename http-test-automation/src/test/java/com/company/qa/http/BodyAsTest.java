package com.company.qa.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodyAsTest {

    /** Reads bodies of the form {@code name=Ada}; anything else is rejected, like a JSON parser would. */
    private static final BodyCodec NAME_CODEC = new BodyCodec() {
        @Override
        @SuppressWarnings("unchecked")
        public <T> T read(String body, Class<T> type) throws Exception {
            if (!body.startsWith("name=")) {
                throw new IllegalArgumentException("not a user: unexpected character '" + body.charAt(0) + "'");
            }
            return (T) new User(body.substring("name=".length()));
        }

        @Override
        public String write(Object model) {
            return "name=" + ((User) model).name;
        }
    };

    @Test
    void bodyAsTurnsTheBodyIntoTheRequestedModelUsingTheConsumersCodec() throws Exception {
        try (TestServer server = new TestServer().on("/u", ex -> TestServer.respond(ex, 200, "name=Ada")).start();
             HttpTransport transport = new HttpTransportBuilder()
                     .baseUrl(server.baseUrl()).bodyCodec(NAME_CODEC).build()) {

            User user = transport.get("/u").bodyAs(User.class);

            assertEquals(new User("Ada"), user);
        }
    }

    @Test
    void bodyAsReadsJsonOutOfTheBoxWithoutAnyConfiguration() throws Exception {
        try (TestServer server = new TestServer()
                .on("/u", ex -> TestServer.respond(ex, 200, "{\"id\":42,\"name\":\"Ada\"}")).start();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {

            Person person = transport.get("/u").bodyAs(Person.class);

            assertEquals(42, person.id);
            assertEquals("Ada", person.name);
        }
    }

    @Test
    void jsonFieldsTheModelDoesNotDeclareAreIgnored() throws Exception {
        try (TestServer server = new TestServer()
                .on("/u", ex -> TestServer.respond(ex, 200, "{\"id\":1,\"name\":\"Ada\",\"newField\":true}")).start();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {

            assertEquals("Ada", transport.get("/u").bodyAs(Person.class).name);
        }
    }

    @Test
    void anExplicitCodecReplacesTheBuiltInOne() throws Exception {
        try (TestServer server = new TestServer().on("/u", ex -> TestServer.respond(ex, 200, "name=Ada")).start();
             HttpTransport transport = new HttpTransportBuilder()
                     .baseUrl(server.baseUrl()).bodyCodec(NAME_CODEC).build()) {

            assertEquals(new User("Ada"), transport.get("/u").bodyAs(User.class));
        }
    }

    @Test
    void aBodyThatIsNotJsonFailsWithStatusUrlAndTheStartOfTheBodyByDefaultToo() throws Exception {
        try (TestServer server = new TestServer()
                .on("/u", ex -> TestServer.respond(ex, 404, "<html>Not Found</html>")).start();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {

            HttpResponse response = transport.get("/u");
            HttpTransportException e = assertThrows(HttpTransportException.class, () -> response.bodyAs(Person.class));

            assertTrue(e.getMessage().contains("404"), e.getMessage());
            assertTrue(e.getMessage().contains(server.baseUrl() + "/u"), e.getMessage());
            assertTrue(e.getMessage().contains("<html>Not Found</html>"), e.getMessage());
        }
    }

    @Test
    void aFailedParseReportsStatusUrlAndOnlyTheStartOfTheBody() throws Exception {
        String html = "<html>" + "x".repeat(500);
        try (TestServer server = new TestServer().on("/u", ex -> TestServer.respond(ex, 404, html)).start();
             HttpTransport transport = new HttpTransportBuilder()
                     .baseUrl(server.baseUrl()).bodyCodec(NAME_CODEC).build()) {

            HttpResponse response = transport.get("/u");
            HttpTransportException e = assertThrows(HttpTransportException.class, () -> response.bodyAs(User.class));

            String message = e.getMessage();
            assertTrue(message.contains("404"), message);
            assertTrue(message.contains(server.baseUrl() + "/u"), message);
            assertTrue(message.contains(html.substring(0, 200)), message);
            assertFalse(message.contains(html.substring(0, 201)), message);
            assertTrue(message.contains("not a user"), message);
        }
    }

    public static class Person {
        public int id;
        public String name;
    }

    private static final class User {
        final String name;

        User(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof User && ((User) o).name.equals(name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }
}
