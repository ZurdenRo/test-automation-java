package com.company.qa.config;

/** A config file is missing, malformed, or holds a value its reader cannot use. The message names key and file. */
public class ConfigException extends RuntimeException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
