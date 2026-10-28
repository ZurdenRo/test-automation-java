package com.company.qa.http.consumer;

import com.company.qa.http.HttpServiceClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** A consumer's service client reads its secrets from the process environment, through its service config. */
class SecretClientTest {

    @TempDir
    Path classpathDir;

    private final ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();

    @AfterEach
    void restoreClassLoader() {
        Thread.currentThread().setContextClassLoader(originalLoader);
    }

    static class SearchClient extends HttpServiceClient {

        SearchClient() {
            super("search", "qa");
        }

        String searchPath() {
            return secret("search-path");
        }
    }

    @Test
    void aServiceClientReadsTheVariableItsServiceConfigNames() throws IOException {
        // PATH is set on every machine, so it stands in for a real secret.
        Files.writeString(classpathDir.resolve("test-automation-qa.yaml"),
                "services:\n  search:\n    base-url: http://127.0.0.1:1\n    secrets:\n      search-path: PATH\n");
        Thread.currentThread().setContextClassLoader(
                new URLClassLoader(new URL[]{classpathDir.toUri().toURL()}, originalLoader));

        try (SearchClient client = new SearchClient()) {
            assertFalse(client.searchPath().isEmpty());
        }
    }
}
