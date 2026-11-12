package com.company.qa.http.consumer;

import com.company.qa.http.HttpResponse;
import com.company.qa.http.HttpServiceClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testng.ITestResult;
import org.testng.TestListenerAdapter;
import org.testng.TestNG;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A client built with only its service's name runs on the environment of the TestNG suite that creates it. The
 * suites are consumer-shaped and run here the way a consumer's runner would.
 */
class SuiteEnvironmentTest {

    /** What the consumer's tests saw, one entry per test method: the environment that answered. */
    private static final List<String> SEEN = Collections.synchronizedList(new ArrayList<>());

    /** The consumer's client: it names its service and nothing else. */
    static class UsersClient extends HttpServiceClient {

        UsersClient() {
            super("users");
        }

        HttpResponse who() {
            return get("/who");
        }
    }

    /** The consumer's test: it creates the client before its tests, as the javadoc asks. */
    public static class CallsUsersTest {

        private UsersClient users;

        @BeforeClass
        public void openClient() {
            users = new UsersClient();
        }

        @org.testng.annotations.Test
        public void asksWhoAnswers() {
            SEEN.add(users.who().body());
        }

        @AfterClass(alwaysRun = true)
        public void closeClient() throws IOException {
            if (users != null) {
                users.close();
            }
        }
    }

    @TempDir
    Path classpathDir;

    private final List<HttpServer> servers = new ArrayList<>();

    @BeforeEach
    void writeTheConfigOfQaAndStaging() throws IOException {
        SEEN.clear();
        config("qa");
        config("staging");
    }

    @AfterEach
    void stopServers() {
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void theClientRunsOnTheEnvironmentTheSuiteNames() {
        Results results = run(Map.of("env", "staging"), Map.of("test", Map.of()));

        results.assertAllPassed();
        assertEquals(List.of("staging"), SEEN);
    }

    @Test
    void withoutAnEnvParameterTheClientRunsOnQa() {
        Results results = run(Map.of(), Map.of("test", Map.of()));

        results.assertAllPassed();
        assertEquals(List.of("qa"), SEEN);
    }

    @Test
    void eachXmlTestRunsItsClientOnTheEnvironmentItsParametersName() {
        Map<String, Map<String, String>> tests = new LinkedHashMap<>();
        tests.put("on qa", Map.of("env", "qa"));
        tests.put("on staging", Map.of("env", "staging"));

        Results results = run(Map.of(), tests);

        results.assertAllPassed();
        assertEquals(List.of("qa", "staging"), SEEN);
    }

    @Test
    void aMissingEnvironmentFileNamesTheClientAndTheParameterThatNamedIt() {
        Results results = run(Map.of("env", "prod"), Map.of("test", Map.of()));

        assertEquals("Config file 'test-automation-prod.yaml' not found on the classpath (needed by service client"
                + " 'users', cause: the suite xml sets <parameter name=\"env\" value=\"prod\"/>; add the file or fix"
                + " the value)", results.failure("openClient").getMessage());
    }

    @Test
    void withoutAnEnvParameterAMissingQaFileSaysTheParameterIsMissing() throws IOException {
        Files.delete(classpathDir.resolve("test-automation-qa.yaml"));

        Results results = run(Map.of(), Map.of("test", Map.of()));

        assertEquals("Config file 'test-automation-qa.yaml' not found on the classpath (needed by service client"
                + " 'users', cause: missing <parameter name=\"env\"> in the suite xml, so the default env 'qa' was"
                + " used; add the parameter or the file)", results.failure("openClient").getMessage());
    }

    @Test
    void aClientCreatedWhereNoTestIsRunningFailsSayingWhereToCreateIt() {
        IllegalStateException e = assertThrows(IllegalStateException.class, UsersClient::new);

        assertEquals("Service client 'users' reads <parameter name=\"env\"> from the running TestNG test, and none"
                + " is running: create it in a @BeforeClass or @BeforeMethod method or in a test, not in a field"
                + " initializer or constructor of the test class", e.getMessage());
    }

    /** A server that answers {@code /who} with the environment's name, and that environment's file pointing at it. */
    private void config(String environment) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/who", ex -> {
            byte[] bytes = environment.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        servers.add(server);
        Files.writeString(classpathDir.resolve("test-automation-" + environment + ".yaml"),
                "services:\n  users:\n    base-url: http://127.0.0.1:" + server.getAddress().getPort() + "\n");
    }

    private Results run(Map<String, String> suiteParameters, Map<String, Map<String, String>> tests) {
        XmlSuite suite = new XmlSuite();
        suite.setName("consumer");
        suite.setParameters(new LinkedHashMap<>(suiteParameters));
        tests.forEach((name, parameters) -> {
            XmlTest test = new XmlTest(suite);
            test.setName(name);
            test.setParameters(new LinkedHashMap<>(parameters));
            test.setXmlClasses(List.of(new XmlClass(CallsUsersTest.class)));
        });
        TestNG testng = new TestNG(false);
        testng.setUseDefaultListeners(false);
        testng.setXmlSuites(List.of(suite));
        TestListenerAdapter listener = new TestListenerAdapter();
        testng.addListener(listener);

        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(new URLClassLoader(new URL[]{classpathDir.toUri().toURL()}, original));
            testng.run();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            thread.setContextClassLoader(original);
        }
        return new Results(listener);
    }

    private static final class Results {

        private final TestListenerAdapter listener;

        private Results(TestListenerAdapter listener) {
            this.listener = listener;
        }

        void assertAllPassed() {
            List<ITestResult> problems = new ArrayList<>(listener.getFailedTests());
            problems.addAll(listener.getSkippedTests());
            problems.addAll(listener.getConfigurationFailures());
            assertTrue(problems.isEmpty() && !listener.getPassedTests().isEmpty(), "problems: " + problems.stream()
                    .map(r -> r.getMethod().getMethodName() + " -> " + r.getThrowable()).collect(Collectors.toList()));
        }

        Throwable failure(String method) {
            return listener.getConfigurationFailures().stream()
                    .filter(r -> r.getMethod().getMethodName().equals(method))
                    .map(ITestResult::getThrowable)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(method + " did not fail"));
        }
    }
}
