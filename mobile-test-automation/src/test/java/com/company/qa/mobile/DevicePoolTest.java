package com.company.qa.mobile;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevicePoolTest {

    private static final AtomicInteger NEXT_SERVER = new AtomicInteger();

    // Pools are shared by server and devices, so each test names a server of its own.
    private final URI server = URI.create("http://pool-test-" + NEXT_SERVER.incrementAndGet() + ":4723");

    @Test
    void eachThreadGetsADeviceNoOtherHolds() {
        DevicePool pool = DevicePool.of("mobile.android.devices", server, List.of("emulator-5554", "emulator-5556"));

        String first = pool.acquire(100);
        String second = pool.acquire(100);

        assertNotEquals(first, second);
    }

    @Test
    void whoeverAsksForTheSameDevicesOnTheSameServerSharesThePool() {
        assertSame(DevicePool.of("mobile.android.devices", server, List.of("emulator-5554")),
                DevicePool.of("mobile.android.devices", server, List.of("emulator-5554")));
    }

    @Test
    void anotherServerOrAnotherListIsAnotherPool() {
        DevicePool pool = DevicePool.of("mobile.android.devices", server, List.of("emulator-5554"));

        assertFalse(pool == DevicePool.of("mobile.android.devices", URI.create("http://other:4723"),
                List.of("emulator-5554")));
        assertFalse(pool == DevicePool.of("mobile.android.devices", server, List.of("emulator-5556")));
    }

    @Test
    void aThreadWaitsForADeviceAnotherReturns() throws Exception {
        DevicePool pool = DevicePool.of("mobile.android.devices", server, List.of("emulator-5554"));
        String held = pool.acquire(100);

        CompletableFuture<String> waiting = CompletableFuture.supplyAsync(() -> pool.acquire(5_000));
        Thread.sleep(100);
        assertFalse(waiting.isDone());
        pool.release(held);

        assertEquals("emulator-5554", waiting.get(5, TimeUnit.SECONDS));
    }

    @Test
    void whenNoDeviceFreesUpTheErrorNamesThePoolAndWhatToChange() {
        DevicePool pool = DevicePool.of("mobile.android.devices", server, List.of("emulator-5554", "emulator-5556"));
        pool.acquire(100);
        pool.acquire(100);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> pool.acquire(50));

        assertEquals("No device of mobile.android.devices (emulator-5554, emulator-5556) was free within 50 ms: more test"
                + " threads than devices, or a session that was never closed. Raise device-wait-timeout-ms, add"
                + " devices to mobile.android.devices, or run fewer threads", e.getMessage());
    }

    @Test
    void anInterruptedWaitKeepsTheInterruptAndFails() {
        DevicePool pool = DevicePool.of("mobile.android.devices", server, List.of("emulator-5554"));
        pool.acquire(100);

        Thread.currentThread().interrupt();
        try {
            assertThrows(IllegalStateException.class, () -> pool.acquire(5_000));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
