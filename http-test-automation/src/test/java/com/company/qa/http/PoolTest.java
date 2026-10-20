package com.company.qa.http;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoolTest {

    @Test
    void consecutiveGetsReuseTheSameConnection() throws Exception {
        try (TestServer server = new TestServer()
                .on("/port", ex -> TestServer.respond(ex, 200, String.valueOf(ex.getRemoteAddress().getPort())))
                .start();
             HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build()) {

            String first = transport.get("/port").body();
            String second = transport.get("/port").body();

            assertEquals(first, second, "the second GET should travel over the connection the first one opened");
        }
    }

    @Test
    void maxConnectionsOfOneMakesParallelGetsWaitTheirTurn() throws Exception {
        assertEquals(1, peakParallelRequests(1));
    }

    @Test
    void maxConnectionsOfTwoLetsTwoGetsRunAtOnce() throws Exception {
        assertEquals(2, peakParallelRequests(2));
    }

    @Test
    void getAfterCloseRaisesOurExceptionSayingTheTransportIsClosed() throws Exception {
        try (TestServer server = new TestServer().on("/x", ex -> TestServer.respond(ex, 200, "ok")).start()) {
            HttpTransport transport = new HttpTransportBuilder().baseUrl(server.baseUrl()).build();
            transport.get("/x");
            transport.close();

            HttpTransportException e = assertThrows(HttpTransportException.class, () -> transport.get("/x"));

            assertTrue(e.getMessage().contains("closed"), e.getMessage());
            assertTrue(e.getMessage().contains(server.baseUrl() + "/x"), e.getMessage());
        }
    }

    /** Fires two GETs at the same moment against a slow handler and reports how many the server saw at once. */
    private static int peakParallelRequests(int maxConnections) throws Exception {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        try (TestServer server = new TestServer().on("/slow", ex -> {
                 peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                 try {
                     Thread.sleep(300);
                 } catch (InterruptedException e) {
                     Thread.currentThread().interrupt();
                 }
                 inFlight.decrementAndGet();
                 TestServer.respond(ex, 200, "done");
             }).start();
             HttpTransport transport = new HttpTransportBuilder()
                     .baseUrl(server.baseUrl()).maxConnections(maxConnections).build()) {

            ExecutorService callers = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Integer>> results = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    results.add(callers.submit(() -> {
                        start.await();
                        return transport.get("/slow").status();
                    }));
                }
                start.countDown();
                for (Future<Integer> result : results) {
                    assertEquals(200, result.get(5, TimeUnit.SECONDS));
                }
            } finally {
                callers.shutdownNow();
            }
        }
        return peak.get();
    }
}
