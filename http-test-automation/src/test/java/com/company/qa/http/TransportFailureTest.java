package com.company.qa.http;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransportFailureTest {

    @Test
    void refusedConnectionRaisesATransportExceptionNamingTheUrl() {
        try (HttpTransport transport = new HttpTransportBuilder().baseUrl("http://127.0.0.1:1").build()) {

            HttpTransportException e = assertThrows(HttpTransportException.class, () -> transport.get("/anything"));

            assertTrue(e.getMessage().contains("http://127.0.0.1:1/anything"), e.getMessage());
        }
    }

    @Test
    void aServerThatAnswersTooSlowlyRaisesATransportExceptionNamingTheUrl() throws Exception {
        try (TestServer server = new TestServer().on("/slow", ex -> {
                 try {
                     Thread.sleep(1_500);
                 } catch (InterruptedException ignored) {
                     Thread.currentThread().interrupt();
                 }
                 TestServer.respond(ex, 200, "late");
             }).start();
             HttpTransport transport = new HttpTransportBuilder()
                     .baseUrl(server.baseUrl()).responseTimeoutMs(200).build()) {

            HttpTransportException e = assertThrows(HttpTransportException.class, () -> transport.get("/slow"));

            assertTrue(e.getMessage().contains(server.baseUrl() + "/slow"), e.getMessage());
        }
    }

    @Test
    void aServerThatNeverAcceptsTheConnectionRaisesATransportExceptionWithinTheConnectTimeout() throws Exception {
        try (BlackHole hole = new BlackHole();
             HttpTransport transport = new HttpTransportBuilder()
                     .baseUrl(hole.baseUrl()).connectTimeoutMs(300).build()) {

            long start = System.nanoTime();
            HttpTransportException e = assertThrows(HttpTransportException.class, () -> transport.get("/hang"));
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertTrue(e.getMessage().contains(hole.baseUrl() + "/hang"), e.getMessage());
            assertTrue(elapsedMs < 3_000, "took " + elapsedMs + " ms, the 10 s default was used");
        }
    }

    /**
     * A listening socket whose accept queue is full: the kernel drops further SYNs,
     * so a connect hangs until the client gives up.
     */
    private static final class BlackHole implements AutoCloseable {

        private final ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        private final List<Socket> fillers = new ArrayList<>();

        BlackHole() throws IOException {
            for (int i = 0; i < 4; i++) {
                Socket filler = new Socket();
                try {
                    filler.connect(server.getLocalSocketAddress(), 200);
                } catch (IOException queueAlreadyFull) {
                    // expected once the queue is full
                }
                fillers.add(filler);
            }
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getLocalPort();
        }

        @Override
        public void close() throws IOException {
            for (Socket filler : fillers) {
                filler.close();
            }
            server.close();
        }
    }
}
