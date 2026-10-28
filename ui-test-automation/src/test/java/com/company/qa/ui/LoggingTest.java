package com.company.qa.ui;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingTest {

    @TempDir
    Path classpathDir;

    private final Logger logger = (Logger) LoggerFactory.getLogger("com.company.qa.ui");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
    private Level originalLevel;

    @BeforeEach
    void capture() {
        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void restore() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
        Thread.currentThread().setContextClassLoader(originalLoader);
    }

    @Test
    void eachTestLogsItsEnvironmentAndTheBrowserItLaunchesAtInfo() {
        ConsumerRun.in(classpathDir)
                .resource("test-automation-staging.yaml", "")
                .suiteParameter("env", "staging")
                .run(DoesNothingTest.class)
                .assertAllPassed();

        List<String> info = messages(Level.INFO);
        assertTrue(info.contains("<test> 'test' runs on env 'staging' (test-automation-staging.yaml)"), info::toString);
        assertTrue(info.stream().anyMatch(m -> m.startsWith("Launching chromium (headless) on thread ")),
                info::toString);
    }

    @Test
    void aSuiteStillSettingUiConfigIsWarnedThatItRunsOnTheDefaultEnvironment() {
        ConsumerRun.in(classpathDir)
                .resource("test-automation-staging.yaml", "")
                .suiteParameter("ui.config", "ui-test-staging.properties")
                .run(DoesNothingTest.class)
                .assertAllPassed();

        assertTrue(messages(Level.WARN).contains("Ignoring <parameter> ui.config 'ui-test-staging.properties':"
                + " <parameter> env replaced it, so <test> 'test' runs on env 'qa' (test-automation-qa.yaml)"),
                () -> messages(Level.WARN).toString());
    }

    @Test
    void aSecretIsLoggedByItsVariableNeverByItsValue() throws IOException {
        Files.writeString(classpathDir.resolve("test-automation-qa.yaml"),
                "ui:\n  secrets:\n    admin-password: SHOP_QA_ADMIN_PASSWORD\n");
        Thread.currentThread().setContextClassLoader(
                new URLClassLoader(new URL[]{classpathDir.toUri().toURL()}, originalLoader));

        UiConfigLoader.load(Map.of(), Map.of("SHOP_QA_ADMIN_PASSWORD", "qa-secret")).secret("admin-password");

        List<String> debug = messages(Level.DEBUG);
        assertTrue(debug.contains("Secret 'admin-password' read from environment variable SHOP_QA_ADMIN_PASSWORD"),
                debug::toString);
        assertFalse(appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("qa-secret")));
    }

    private List<String> messages(Level level) {
        return appender.list.stream()
                .filter(e -> e.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());
    }

    public static class DoesNothingTest extends BaseUiTest {
        @org.testng.annotations.Test
        public void nothing() {
        }
    }
}
