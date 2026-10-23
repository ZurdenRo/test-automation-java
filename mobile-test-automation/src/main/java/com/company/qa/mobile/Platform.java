package com.company.qa.mobile;

/** The mobile platform a suite runs on. iOS is planned (root ADR-0004) and not built yet. */
enum Platform {

    ANDROID("android");

    private final String key;

    Platform(String key) {
        this.key = key;
    }

    /** The name in the {@code platform} suite parameter, and the prefix of this platform's config keys. */
    String key() {
        return key;
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
