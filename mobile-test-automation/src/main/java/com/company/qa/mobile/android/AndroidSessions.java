package com.company.qa.mobile.android;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;

import java.net.MalformedURLException;
import java.net.URI;

/** Starts Android sessions for the library. Public only so the core package can call it: not for consumers. */
public final class AndroidSessions {

    private AndroidSessions() {
    }

    /**
     * @param serial     the device, as {@code adb devices} lists it
     * @param app        the APK to install, as the server resolves it (a path where it runs, or a URL); null if
     *                   {@code appPackage} is set
     * @param appPackage an app already installed; null if {@code app} is set
     */
    public static AndroidDriver start(URI server, String serial, String app, String appPackage) {
        UiAutomator2Options options = new UiAutomator2Options()
                .setUdid(serial)
                // Clears the app's data before the session, so each test starts clean without a reinstall.
                .setNoReset(false);
        if (app != null) {
            options.setApp(app);
        } else {
            options.setAppPackage(appPackage);
        }
        try {
            return new AndroidDriver(server.toURL(), options);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Not a URL: " + server, e);
        }
    }
}
