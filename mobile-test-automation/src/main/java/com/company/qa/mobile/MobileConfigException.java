package com.company.qa.mobile;

/** The mobile config is missing, unreadable or holds an invalid value. The message says which and where. */
public class MobileConfigException extends RuntimeException {

    public MobileConfigException(String message) {
        super(message);
    }

    public MobileConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
