package com.company.qa.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecretsTest {

    @TempDir
    Path classpathDir;

    private final ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();

    @AfterEach
    void restoreClassLoader() {
        Thread.currentThread().setContextClassLoader(originalLoader);
    }

    @Test
    void theServiceConfigNamesTheVariableThatHoldsTheSecret() throws IOException {
        resource("test-automation-qa.yaml", service("payments", "base-url: http://127.0.0.1:1", "secrets:",
                "  api-token: PAYMENTS_QA_TOKEN"));

        try (HttpTransport transport = new HttpTransportBuilder().service("payments", "qa")
                .secretsFrom(Map.of("PAYMENTS_QA_TOKEN", "qa-token", "PAYMENTS_DEV_TOKEN", "dev-token")).build()) {
            assertEquals("qa-token", transport.secret("api-token"));
        }
    }

    @Test
    void aSecretTheServiceConfigDoesNotMapIsNamedWithTheKeyToAdd() throws IOException {
        resource("test-automation-qa.yaml", service("payments", "base-url: http://127.0.0.1:1"));

        try (HttpTransport transport = new HttpTransportBuilder().service("payments", "qa")
                .secretsFrom(Map.of()).build()) {
            HttpTransportException e = assertThrows(HttpTransportException.class, () -> transport.secret("api-token"));

            assertEquals("Missing 'services.payments.secrets.api-token' in test-automation-qa.yaml: it names the"
                    + " environment variable that holds secret 'api-token'", e.getMessage());
        }
    }

    @Test
    void aSecretWithoutAServiceConfigSaysOneIsNeeded() {
        try (HttpTransport transport = new HttpTransportBuilder().baseUrl("http://127.0.0.1:1")
                .secretsFrom(Map.of()).build()) {
            HttpTransportException e = assertThrows(HttpTransportException.class, () -> transport.secret("api-token"));

            assertEquals("Missing 'secrets.api-token': there is no service config to name the environment variable"
                    + " that holds secret 'api-token'", e.getMessage());
        }
    }

    @Test
    void anUnsetVariableIsNamedWithoutRevealingAnything() throws IOException {
        resource("test-automation-qa.yaml", service("payments", "base-url: http://127.0.0.1:1", "secrets:",
                "  api-token: PAYMENTS_QA_TOKEN"));

        try (HttpTransport transport = new HttpTransportBuilder().service("payments", "qa")
                .secretsFrom(Map.of("PAYMENTS_QA_TOKEN", " ")).build()) {
            HttpTransportException e = assertThrows(HttpTransportException.class, () -> transport.secret("api-token"));

            assertEquals("Secret 'api-token' needs environment variable PAYMENTS_QA_TOKEN, which is not set (blank"
                    + " values count as missing; test-automation-qa.yaml names it)", e.getMessage());
        }
    }

    /** The file of the qa environment holding one service's settings, one per line. */
    private static String service(String name, String... lines) {
        StringBuilder yaml = new StringBuilder("services:\n  " + name + ":\n");
        for (String line : lines) {
            yaml.append("    ").append(line).append('\n');
        }
        return yaml.toString();
    }

    private void resource(String name, String content) throws IOException {
        Files.writeString(classpathDir.resolve(name), content);
        Thread.currentThread().setContextClassLoader(
                new URLClassLoader(new URL[]{classpathDir.toUri().toURL()}, originalLoader));
    }
}
