package com.company.qa.mobile;

import com.company.qa.mobile.android.AndroidSessions;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.logging.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * One test method's Appium session, knowing no test runner. It takes a device from the pool for as long as it
 * lives, so nothing one test leaves behind reaches another.
 */
final class MobileSession {

    private static final Logger LOG = LoggerFactory.getLogger(MobileSession.class);

    static {
        // JDK 11's HttpClient, which the Appium client talks through, trips an assert of its own reading a large
        // body (a screenshot, a UI tree) when assertions are on, as Gradle and Surefire run tests by default. Its
        // classes are loaded by the platform class loader, so -ea reaches them. Turning them off for that package
        // only, before the first session loads them, leaves the consumer's own asserts alone. Fixed in JDK 17.
        ClassLoader.getPlatformClassLoader().setPackageAssertionStatus("jdk.internal.net.http", false);
    }

    private final MobileConfig config;
    private final DevicePool pool;
    private final String serial;
    private final AppiumDriver driver;

    private MobileSession(MobileConfig config, DevicePool pool, String serial, AppiumDriver driver) {
        this.config = config;
        this.pool = pool;
        this.serial = serial;
        this.driver = driver;
    }

    /** Takes a free device, waiting for one, and starts a session on it. */
    static MobileSession start(MobileConfig config) {
        String devices = config.platform().key() + "." + MobileConfigLoader.DEVICES;
        DevicePool pool = DevicePool.of(devices, config.appiumServerUrl(), config.devices());
        String serial = pool.acquire(config.deviceWaitTimeoutMs());
        AppiumDriver driver = null;
        try {
            LOG.debug("Starting a session on {} at {}", serial, config.appiumServerUrl());
            driver = AndroidSessions.start(config.appiumServerUrl(), serial, config.app(), config.appPackage());
            driver.manage().timeouts().implicitlyWait(Duration.ZERO);
            Waits.register(driver, config.waitUntilTimeoutMs());
            return new MobileSession(config, pool, serial, driver);
        } catch (RuntimeException e) {
            quitQuietly(driver, serial);
            pool.release(serial);
            throw new IllegalStateException("Could not start a session on device '" + serial + "' at Appium server "
                    + config.appiumServerUrl() + ": is the server up, the device listed by `adb devices` and"
                    + " the app readable by the server? " + firstLine(e.getMessage()), e);
        }
    }

    String environment() {
        return config.environment();
    }

    String secret(String name) {
        return config.secret(name);
    }

    /** The screen the device shows now, without doing anything. */
    <T extends BaseScreen> T on(Class<T> type) {
        for (Constructor<?> constructor : type.getConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length == 1 && parameters[0].isInstance(driver)) {
                try {
                    return type.cast(constructor.newInstance(driver));
                } catch (InvocationTargetException e) {
                    throw new IllegalStateException("Creating " + type.getSimpleName() + " failed", e.getCause());
                } catch (ReflectiveOperationException e) {
                    throw new IllegalArgumentException("Cannot create " + type.getSimpleName(), e);
                }
            }
        }
        throw new IllegalArgumentException(type.getSimpleName() + " needs a public constructor taking the driver"
                + " (" + driver.getClass().getSimpleName() + " or a supertype)");
    }

    /**
     * Keeps the failure evidence of a failed test, then closes the session and returns its device to the pool, even
     * if closing fails. Evidence that cannot be taken (a session the server lost) is skipped, so it never hides why
     * the test failed.
     *
     * @param testClass  names the evidence directory
     * @param testMethod names the evidence files; a second failure of the same method gets {@code -2}
     * @return the evidence files kept, none for a test that passed
     */
    List<Path> close(String testClass, String testMethod, boolean failed) {
        List<Path> evidence = new ArrayList<>();
        try {
            if (failed) {
                Path stem = unusedStem(config.evidenceDir().resolve(testClass), testMethod);
                keep(evidence, sibling(stem, ".png"), () -> driver.getScreenshotAs(OutputType.BYTES));
                keep(evidence, sibling(stem, ".xml"),
                        () -> driver.getPageSource().getBytes(StandardCharsets.UTF_8));
                String logType = config.platform().logType();
                keep(evidence, sibling(stem, "-" + logType + ".txt"), () -> deviceLog(logType));
            }
        } finally {
            try {
                Waits.unregister(driver);
                quitQuietly(driver, serial);
            } finally {
                pool.release(serial);
            }
        }
        return evidence;
    }

    /** What the device logged since the session started: the session is the test's, so this is only its lines. */
    private byte[] deviceLog(String logType) {
        StringBuilder log = new StringBuilder();
        for (LogEntry entry : driver.manage().logs().get(logType)) {
            log.append(entry.getMessage()).append('\n');
        }
        return log.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void keep(List<Path> evidence, Path file, java.util.function.Supplier<byte[]> content) {
        try {
            byte[] bytes = content.get();
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
            evidence.add(file);
        } catch (RuntimeException | IOException e) {
            LOG.warn("Could not keep {} on {}: {}", file.getFileName(), serial, firstLine(e.getMessage()));
            LOG.debug("Why {} could not be kept", file.getFileName(), e);
        }
    }

    private static Path unusedStem(Path dir, String testMethod) {
        Path stem = dir.resolve(testMethod);
        for (int n = 2; Files.exists(sibling(stem, ".png")) || Files.exists(sibling(stem, ".xml")); n++) {
            stem = dir.resolve(testMethod + "-" + n);
        }
        return stem;
    }

    private static Path sibling(Path stem, String suffix) {
        return stem.resolveSibling(stem.getFileName() + suffix);
    }

    private static void quitQuietly(AppiumDriver driver, String serial) {
        if (driver != null) {
            try {
                driver.quit();
            } catch (RuntimeException e) {
                LOG.warn("Could not close the session on {}: {}", serial, firstLine(e.getMessage()));
            }
        }
    }

    private static String firstLine(String message) {
        return message == null ? "" : message.split("\n", 2)[0];
    }
}
