package com.company.qa.mobile;

import java.net.URI;
import java.util.List;

/** The settings of one test method's session, resolved from every source. */
final class MobileConfig {

    private final MobileConfigLoader loader;
    private final String environment;
    private final Platform platform;
    private final URI appiumServerUrl;
    private final int waitUntilTimeoutMs;
    private final int deviceWaitTimeoutMs;
    private final String app;
    private final String appPackage;
    private final List<String> devices;

    MobileConfig(MobileConfigLoader loader, String environment, Platform platform, URI appiumServerUrl,
                 int waitUntilTimeoutMs, int deviceWaitTimeoutMs, String app, String appPackage,
                 List<String> devices) {
        this.loader = loader;
        this.environment = environment;
        this.platform = platform;
        this.appiumServerUrl = appiumServerUrl;
        this.waitUntilTimeoutMs = waitUntilTimeoutMs;
        this.deviceWaitTimeoutMs = deviceWaitTimeoutMs;
        this.app = app;
        this.appPackage = appPackage;
        this.devices = devices;
    }

    /** A secret by its name in {@code secret.<name>}, read from the environment variable the file names. */
    String secret(String name) {
        return loader.secret(name);
    }

    /** The suite's environment, qa unless {@code <parameter> env} names another. */
    String environment() {
        return environment;
    }

    /** The suite's platform, android unless {@code <parameter> platform} names another. */
    Platform platform() {
        return platform;
    }

    /** Where the Appium server is. The library connects to it and never starts one (ADR-0003). */
    URI appiumServerUrl() {
        return appiumServerUrl;
    }

    /** How long a screen's {@code waitUntil} waits before failing; the implicit wait stays at 0 (ADR-0002). */
    int waitUntilTimeoutMs() {
        return waitUntilTimeoutMs;
    }

    /** How long a test thread waits for a free device of the pool before failing (ADR-0004). */
    int deviceWaitTimeoutMs() {
        return deviceWaitTimeoutMs;
    }

    /** The APK to install, as the Appium server resolves it (a path where it runs, or a URL); null if the app is installed. */
    String app() {
        return app;
    }

    /** The package of an app already installed on the device; null if {@link #app()} is set. */
    String appPackage() {
        return appPackage;
    }

    /** The serials of the platform's device pool, in the order the file lists them. */
    List<String> devices() {
        return devices;
    }
}
