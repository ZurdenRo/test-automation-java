package com.company.qa.mobile;

/** The mobile platform a suite runs on. iOS is planned (root ADR-0004) and not built yet. */
enum Platform {

    ANDROID("android", "logcat");

    private final String key;
    private final String logType;

    Platform(String key, String logType) {
        this.key = key;
        this.logType = logType;
    }

    /** The name in the {@code platform} suite parameter, and the prefix of this platform's config keys. */
    String key() {
        return key;
    }

    /** The name Appium gives this platform's device log, which failure evidence keeps. */
    String logType() {
        return logType;
    }

    static Platform ofKey(String key) {
        for (Platform platform : values()) {
            if (platform.key.equals(key)) {
                return platform;
            }
        }
        return null;
    }
}
