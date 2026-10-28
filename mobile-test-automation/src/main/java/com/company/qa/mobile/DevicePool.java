package com.company.qa.mobile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The devices a platform lists, shared by every test thread in the JVM: a device serves one session at a time, so
 * a thread takes a free one and returns it when its session closes (ADR-0004).
 */
final class DevicePool {

    private static final Logger LOG = LoggerFactory.getLogger(DevicePool.class);
    private static final ConcurrentMap<String, DevicePool> POOLS = new ConcurrentHashMap<>();

    private final String name;
    private final List<String> serials;
    private final BlockingQueue<String> free;

    private DevicePool(String name, List<String> serials) {
        this.name = name;
        this.serials = serials;
        this.free = new LinkedBlockingQueue<>(serials);
    }

    /**
     * The pool for these devices on this server; the same one for every caller that names them again, so threads
     * share it. A run whose {@code <test>}s name other servers or devices has one pool for each.
     *
     * @param name what the config calls the list, for messages ({@code mobile.android.devices})
     */
    static DevicePool of(String name, URI server, List<String> serials) {
        return POOLS.computeIfAbsent(server + " " + name + "=" + serials, key -> {
            LOG.info("Device pool {} on {}: {}", name, server, serials);
            return new DevicePool(name, serials);
        });
    }

    /** Takes a free device, waiting for one if all are in use. */
    String acquire(long timeoutMs) {
        try {
            String serial = free.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (serial == null) {
                throw new IllegalStateException("No device of " + name + " (" + String.join(", ", serials)
                        + ") was free within " + timeoutMs + " ms: more test threads than devices, or a session"
                        + " that was never closed. Raise device-wait-timeout-ms, add devices to " + name
                        + ", or run fewer threads");
            }
            return serial;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a free device of " + name, e);
        }
    }

    void release(String serial) {
        free.add(serial);
    }
}
