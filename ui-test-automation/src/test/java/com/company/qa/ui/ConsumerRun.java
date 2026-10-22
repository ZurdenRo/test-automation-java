package com.company.qa.ui;

import org.testng.ITestResult;
import org.testng.TestListenerAdapter;
import org.testng.TestNG;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Runs consumer-shaped TestNG classes the way a consumer's suite would, with a {@code ui-test-qa.properties} (the
 * default config resource) written for the run and put on the classpath, and reports how each test method ended.
 */
final class ConsumerRun {

    private final Path classpathDir;
    private final StringBuilder config = new StringBuilder();
    private final Map<String, Map<String, String>> tests = new LinkedHashMap<>();
    private final Map<String, String> suiteParameters = new LinkedHashMap<>();
    private String parallel;

    private ConsumerRun(Path classpathDir) {
        this.classpathDir = classpathDir;
    }

    /** @param classpathDir an empty directory where the run's config resources are written */
    static ConsumerRun in(Path classpathDir) {
        return new ConsumerRun(classpathDir);
    }

    ConsumerRun config(String key, String value) {
        config.append(key).append('=').append(value).append('\n');
        return this;
    }

    /** A {@code <test>} with its own {@code <parameter>}s; without any, the run is a single {@code <test>}. */
    ConsumerRun xmlTest(String name, Map<String, String> parameters) {
        tests.put(name, parameters);
        return this;
    }

    /** A {@code <parameter>} of the {@code <suite>}, which every {@code <test>} in it inherits. */
    ConsumerRun suiteParameter(String name, String value) {
        suiteParameters.put(name, value);
        return this;
    }

    ConsumerRun parallelMethods() {
        parallel = "methods";
        return this;
    }

    /** Writes another config resource, for a suite that names its environment's. */
    ConsumerRun resource(String name, String content) {
        write(name, content);
        return this;
    }

    Results run(Class<?>... classes) {
        write("ui-test-qa.properties", config.toString());
        XmlSuite suite = new XmlSuite();
        suite.setName("consumer");
        suite.setParameters(new LinkedHashMap<>(suiteParameters));
        if (parallel != null) {
            suite.setParallel(XmlSuite.ParallelMode.METHODS);
            suite.setThreadCount(4);
        }
        Map<String, Map<String, String>> xmlTests = tests.isEmpty() ? Map.of("test", Map.of()) : tests;
        xmlTests.forEach((name, parameters) -> {
            XmlTest test = new XmlTest(suite);
            test.setName(name);
            test.setParameters(new LinkedHashMap<>(parameters));
            List<XmlClass> xmlClasses = new ArrayList<>();
            for (Class<?> type : classes) {
                xmlClasses.add(new XmlClass(type));
            }
            test.setXmlClasses(xmlClasses);
        });

        TestNG testng = new TestNG(false);
        testng.setUseDefaultListeners(false);
        testng.setXmlSuites(List.of(suite));
        TestListenerAdapter listener = new TestListenerAdapter();
        testng.addListener(listener);

        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        thread.setContextClassLoader(classLoader());
        try {
            testng.run();
        } finally {
            thread.setContextClassLoader(original);
        }
        return new Results(listener);
    }

    private ClassLoader classLoader() {
        try {
            return new URLClassLoader(new URL[]{classpathDir.toUri().toURL()}, getClass().getClassLoader());
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void write(String name, String content) {
        try {
            Files.writeString(classpathDir.resolve(name), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static final class Results {

        private final TestListenerAdapter listener;

        private Results(TestListenerAdapter listener) {
            this.listener = listener;
        }

        List<String> passed() {
            return names(listener.getPassedTests());
        }

        List<String> failed() {
            return names(listener.getFailedTests());
        }

        /** Why a test method failed, or skipped because a configuration method failed. */
        Throwable failure(String method) {
            List<ITestResult> all = new ArrayList<>(listener.getFailedTests());
            all.addAll(listener.getSkippedTests());
            all.addAll(listener.getConfigurationFailures());
            return all.stream()
                    .filter(r -> r.getMethod().getMethodName().equals(method))
                    .map(ITestResult::getThrowable)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(method + " did not fail; " + summary()));
        }

        /** Throws with every failure's message unless all test methods passed. */
        Results assertAllPassed() {
            if (!listener.getFailedTests().isEmpty() || !listener.getSkippedTests().isEmpty()
                    || !listener.getConfigurationFailures().isEmpty() || listener.getPassedTests().isEmpty()) {
                throw new AssertionError(summary());
            }
            return this;
        }

        String summary() {
            List<ITestResult> problems = new ArrayList<>(listener.getFailedTests());
            problems.addAll(listener.getSkippedTests());
            problems.addAll(listener.getConfigurationFailures());
            return "passed " + passed() + ", problems: " + problems.stream()
                    .map(r -> r.getMethod().getMethodName() + " -> " + r.getThrowable())
                    .collect(Collectors.joining("; "));
        }

        private static List<String> names(List<ITestResult> results) {
            return results.stream().map(r -> r.getMethod().getMethodName()).collect(Collectors.toList());
        }
    }
}
