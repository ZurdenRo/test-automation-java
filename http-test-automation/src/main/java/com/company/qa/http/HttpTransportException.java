package com.company.qa.http;

/**
 * Raised when the library cannot do what was asked: no response was obtained (a transport failure), the service
 * config is missing or invalid, or a response body cannot be read as the requested model.
 */
public class HttpTransportException extends RuntimeException {

    public HttpTransportException(String message) {
        super(message);
    }

    public HttpTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
