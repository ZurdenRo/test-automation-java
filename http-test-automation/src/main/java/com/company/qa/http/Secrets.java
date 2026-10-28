package com.company.qa.http;

import com.company.qa.config.ConfigException;
import com.company.qa.config.Section;
import com.company.qa.config.Setting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * A service client's secrets: {@code secrets.<name>} in its section of the config names the environment variable
 * that holds each one, so each environment's file can point at that environment's variables. A value never appears
 * in a message.
 */
final class Secrets {

    private static final Logger LOG = LoggerFactory.getLogger(Secrets.class);

    private final Section service;
    private final Map<String, String> env;

    /** @param service the client's section of the config, or null when there is none */
    Secrets(Section service, Map<String, String> env) {
        this.service = service;
        this.env = env;
    }

    String get(String name) {
        if (service == null) {
            throw new HttpTransportException("Missing 'secrets." + name + "': there is no service config to name the"
                    + " environment variable that holds secret '" + name + "'");
        }
        String value;
        try {
            value = service.secret(name, env);
        } catch (ConfigException e) {
            throw new HttpTransportException(e.getMessage(), e);
        }
        LOG.debug("Secret '{}' read from environment variable {}", name,
                service.find("secrets." + name).map(Setting::value).orElse("?"));
        return value;
    }
}
