package com.company.qa.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The rule every module applies to a TestNG {@code <test>}'s parameters to pick its environment. */
class EnvironmentParameterTest {

    @Test
    void theEnvParameterNamesTheEnvironmentTrimmed() {
        assertEquals("staging", Config.environmentFrom(Map.of("env", " staging ")));
        assertEquals("cause: the suite xml sets <parameter name=\"env\" value=\"staging\"/>; add the file or fix"
                + " the value", Config.possibleEnvironmentErrorMsg(Map.of("env", " staging ")));
    }

    @Test
    void withoutTheParameterOrWithABlankOneTheEnvironmentIsQa() {
        assertEquals("qa", Config.environmentFrom(Map.of()));
        assertEquals("qa", Config.environmentFrom(Map.of("env", "  ")));
        assertEquals("cause: missing <parameter name=\"env\"> in the suite xml, so the default env 'qa' was used;"
                + " add the parameter or the file", Config.possibleEnvironmentErrorMsg(Map.of()));
    }
}
