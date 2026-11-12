package com.company.qa.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reads the files from the classpath, the way a consumer's tests have them in src/test/resources. */
class LoadTest {

    @Test
    void readsTheSharedFileAndTheEnvironmentFileFromTheClasspath() {
        Config config = Config.load("staging", null);

        assertEquals("staging", config.environment());
        assertEquals(List.of("test-automation.yaml", "test-automation-staging.yaml"), config.files());
        assertEquals("https://users.staging", config.section("services.users").require("base-url", "").value());
        assertEquals("30000", config.section("services.users").require("response-timeout-ms", "").value());
    }

    @Test
    void aMissingEnvironmentFileNamesTheFileAndWhyItWasLookedFor() {
        ConfigException e = assertThrows(ConfigException.class,
                () -> Config.load("stagin", "cause: a typo"));

        assertEquals("Config file 'test-automation-stagin.yaml' not found on the classpath (cause: a typo)",
                e.getMessage());
    }

    @Test
    void anEnvironmentThatIsNotAFileNameFragmentFails() {
        ConfigException e = assertThrows(ConfigException.class, () -> Config.load("../qa", null));

        assertTrue(e.getMessage().startsWith("Environment '../qa' is not a name"), e.getMessage());
    }
}
