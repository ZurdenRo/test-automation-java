package com.company.qa.mobile;

import com.company.qa.mobile.android.AndroidSessions;
import io.appium.java_client.AppiumDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;

/**
 * One test method's Appium session, knowing no test runner. It takes a device from the pool for as long as it
 * lives, so nothing one test leaves behind reaches another.
 */
final class MobileSession {

    private static final Logger LOG = LoggerFactory.getLogger(MobileSession.class);

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

    /** Closes the session and returns its device to the pool, even if closing fails. */
    void close() {
        try {
            Waits.unregister(driver);
            quitQuietly(driver, serial);
        } finally {
            pool.release(serial);
        }
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
