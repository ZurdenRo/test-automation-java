package com.company.qa.http;

import com.company.qa.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestResult;
import org.testng.Reporter;

import java.util.Map;
import java.util.Objects;

/**
 * The environment of the TestNG {@code <test>} running on this thread, read from its {@code <parameter>}s (the
 * suite's included) by the rule every module applies (root docs/adr/0002 and 0008).
 */
final class SuiteEnvironment {

    private static final Logger LOG = LoggerFactory.getLogger(SuiteEnvironment.class);

    private SuiteEnvironment() {
    }

    /** A builder for {@code service} on the running test's environment. */
    static HttpTransportBuilder builderFor(String service) {
        Objects.requireNonNull(service, "service");
        // TestNG sets the current result around every test and configuration method, threads they start included,
        // and clears it when the run ends; it is null while TestNG builds the test class.
        ITestResult current = Reporter.getCurrentTestResult();
        if (current == null) {
            throw new IllegalStateException("Service client '" + service + "' reads <parameter name=\""
                    + Config.ENV_PARAMETER + "\"> from the running TestNG test, and none is running: create it in a"
                    + " @BeforeClass or @BeforeMethod method or in a test, not in a field initializer or constructor"
                    + " of the test class");
        }
        Map<String, String> parameters = current.getTestContext().getCurrentXmlTest().getAllParameters();
        String environment = Config.environmentFrom(parameters);
        String causeMsg = Config.environmentCauseMsg(parameters);
        LOG.debug("Service client '{}' runs on env '{}'", service, environment);
        return new HttpTransportBuilder().service(service, environment, causeMsg);
    }
}
