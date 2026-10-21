package com.company.qa.ui;

/** The UI config is missing, unreadable or holds an invalid value. The message says which and where. */
public class UiConfigException extends RuntimeException {

    public UiConfigException(String message) {
        super(message);
    }

    public UiConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
