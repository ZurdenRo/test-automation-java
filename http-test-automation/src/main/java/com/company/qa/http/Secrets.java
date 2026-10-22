package com.company.qa.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Properties;

/**
 * A service client's secrets: {@code secret.<name>} in its service config names the environment variable that holds
 * each one, so a service config for each environment can point at that environment's variables. A value never
 * appears in a message.
 */
final class Secrets {

    private static final String PREFIX = "secret.";
    private static final Logger LOG = LoggerFactory.getLogger(Secrets.class);

    private final String resource;
    private final Properties file;
    private final Map<String, String> env;

    /** @param resource the service config's name, or null when there is none */
    Secrets(String resource, Properties file, Map<String, String> env) {
        this.resource = resource;
        this.file = file;
        this.env = env;
    }

    String get(String name) {
        String key = PREFIX + name;
        if (resource == null) {
            throw new HttpTransportException("Missing '" + key + "': there is no service config to name the"
                    + " environment variable that holds secret '" + name + "'");
        }
        String variable = file.getProperty(key);
        if (isBlank(variable)) {
            throw new HttpTransportException("Missing '" + key + "' in service config '" + resource + "': it names"
                    + " the environment variable that holds secret '" + name + "'");
        }
        String value = env.get(variable.trim());
        if (isBlank(value)) {
            throw new HttpTransportException("Secret '" + name + "' needs environment variable " + variable.trim()
                    + ", which is not set (blank values count as missing; service config '" + resource
                    + "' names it)");
        }
        LOG.debug("Secret '{}' read from environment variable {}", name, variable.trim());
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
