package com.company.qa.mobile;

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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Runs consumer-shaped TestNG classes the way a consumer's suite would, with a {@code test-automation-qa.yaml} (the
 * default config resource) written for the run and put on the classpath, and reports how each test method ended.
 */
final class ConsumerRun {

    private final Path classpathDir;
    private final Map<String, String> config = new LinkedHashMap<>();
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

    /**
     * A setting of the {@code mobile} section, by its dotted path ({@code android.app}); a value of
     * {@code android.devices} is a comma separated list.
     */
    ConsumerRun config(String key, String value) {
        config.put(key, value);
        return this;
    }

    /** The {@code mobile} section of a config file, nested from dotted paths. */
    static String mobileYaml(Map<String, String> settings) {
        if (settings.isEmpty()) {
            return "";
        }
        Map<String, Object> tree = new LinkedHashMap<>();
        settings.forEach((path, value) -> {
            String[] names = path.split("\\.");
            Map<String, Object> level = tree;
            for (int i = 0; i < names.length - 1; i++) {
                @SuppressWarnings("unchecked")
                Map<String, Object> child = (Map<String, Object>) level.computeIfAbsent(names[i],
                        n -> new LinkedHashMap<String, Object>());
                level = child;
            }
            level.put(names[names.length - 1], path.endsWith("devices") ? Arrays.asList(value.split(",")) : value);
        });
        StringBuilder yaml = new StringBuilder("mobile:\n");
        emit(yaml, tree, 1);
        return yaml.toString();
    }

    private static void emit(StringBuilder yaml, Map<String, Object> level, int depth) {
        level.forEach((name, value) -> {
            yaml.append("  ".repeat(depth)).append(name).append(':');
            if (value instanceof Map) {
                yaml.append('\n');
                @SuppressWarnings("unchecked")
                Map<String, Object> child = (Map<String, Object>) value;
                emit(yaml, child, depth + 1);
            } else if (value instanceof List) {
                yaml.append('\n');
                for (Object item : (List<?>) value) {
                    yaml.append("  ".repeat(depth + 1)).append("- '").append(item).append("'\n");
                }
            } else {
                yaml.append(" '").append(((String) value).replace("'", "''")).append("'\n");
            }
        });
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
        write("test-automation-qa.yaml", mobileYaml(config));
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
