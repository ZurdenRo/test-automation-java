package com.company.qa.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {

    private static final String COMMON = String.join("\n",
            "ui:",
            "  browser: chromium",
            "  timeout-ms: 30000",
            "  viewport: { width: 1280, height: 720 }",
            "mobile:",
            "  android:",
            "    devices: [emulator-5554, emulator-5556]");

    private static String message(Runnable action) {
        return assertThrows(ConfigException.class, action::run).getMessage();
    }

    @Test
    void theEnvironmentFileLaysOverTheSharedOneKeyByKey() {
        Config config = Config.of("staging", COMMON, "ui:\n  timeout-ms: 60000\n  apps:\n    shop: https://s\n");

        Section ui = config.section("ui");
        assertEquals("60000", ui.require("timeout-ms", "").value());
        assertEquals("test-automation-staging.yaml", ui.require("timeout-ms", "").source());
        assertEquals("chromium", ui.require("browser", "").value());
        assertEquals("test-automation.yaml", ui.require("browser", "").source());
        assertEquals("1280", ui.require("viewport.width", "").value());
        assertEquals(List.of("test-automation.yaml", "test-automation-staging.yaml"), config.files());
    }

    @Test
    void aListIsReplacedWholeNotAppended() {
        Config config = Config.of("qa", COMMON, "mobile:\n  android:\n    devices: [emulator-5560]\n");

        List<Setting> devices = config.section("mobile.android").list("devices").get();

        assertEquals(1, devices.size());
        assertEquals("emulator-5560", devices.get(0).value());
    }

    @Test
    void aBlankOrNullValueNeverHidesTheSharedOne() {
        Config config = Config.of("qa", COMMON, "ui:\n  browser:\n  timeout-ms: null\n  viewport: { width: '' }\n");

        Section ui = config.section("ui");
        assertEquals("chromium", ui.require("browser", "").value());
        assertEquals("30000", ui.require("timeout-ms", "").value());
        assertEquals("1280", ui.require("viewport.width", "").value());
    }

    @Test
    void theSharedFileIsOptional() {
        Config config = Config.of("qa", null, "ui:\n  browser: firefox\n");

        assertEquals(List.of("test-automation-qa.yaml"), config.files());
        assertEquals("firefox", config.section("ui").require("browser", "").value());
    }

    @Test
    void valuesAreTextSoQuotingDoesNotChangeThemAndYaml11BooleansAreNot() {
        Config config = Config.of("qa", null,
                "ui:\n  timeout-ms: '30000'\n  headless: true\n  trace: off\n  slow-mo-ms: 0\n");

        Section ui = config.section("ui");
        assertEquals(30000, ui.require("timeout-ms", "").positiveInt());
        assertTrue(ui.require("headless", "").bool());
        assertEquals("off", ui.require("trace", "").value());
        assertEquals(0, ui.require("slow-mo-ms", "").nonNegativeInt());
    }

    @Test
    void anInvalidValueNamesTheKeyTheTextAndTheFile() {
        Config config = Config.of("qa", null, "ui:\n  timeout-ms: abc\n");

        assertEquals("'ui.timeout-ms' in test-automation-qa.yaml is 'abc': expected a positive whole number",
                message(() -> config.section("ui").require("timeout-ms", "").positiveInt()));
    }

    @Test
    void aMissingKeyNamesTheFilesItWasLookedInAndKeepsTheHint() {
        Config config = Config.of("qa", COMMON, "ui:\n  browser: firefox\n");

        assertEquals("Missing 'ui.apps.shop' in test-automation.yaml or test-automation-qa.yaml: for page Home"
                        + " (blank values count as missing)",
                message(() -> config.section("ui").require("apps.shop", ": for page Home")));
    }

    @Test
    void anUnknownKeyFailsNamingItAndItsFile() {
        Config config = Config.of("qa", COMMON, "ui:\n  heedless: true\n");

        String message = message(() -> config.section("ui").rejectUnknown(
                List.of("browser", "timeout-ms", "viewport.width", "viewport.height"), List.of("apps", "secrets")));

        assertTrue(message.startsWith("Unknown key 'ui.heedless' in test-automation-qa.yaml"), message);
    }

    @Test
    void namesUnderAnOpenPrefixAreAllowed() {
        Config config = Config.of("qa", null, "ui:\n  apps:\n    shop: https://s\n    billing: https://b\n");

        config.section("ui").rejectUnknown(List.of("browser"), List.of("apps"));

        assertEquals(List.of("billing", "shop"), List.copyOf(config.section("ui.apps").names()));
    }

    @Test
    void anUnknownTopLevelSectionFails() {
        assertEquals("Unknown section 'servics' in test-automation-qa.yaml: expected ui, services or mobile",
                message(() -> Config.of("qa", null, "servics:\n  users:\n    base-url: https://u\n")));
    }

    @Test
    void aDuplicateKeyFails() {
        String message = message(() -> Config.of("qa", null,
                "services:\n  users:\n    base-url: https://a\n    base-url: https://b\n"));

        assertTrue(message.startsWith("Config file 'test-automation-qa.yaml' is not valid YAML"), message);
    }

    @Test
    void aKeyWithADotFails() {
        String message = message(() -> Config.of("qa", null, "ui:\n  viewport.width: 1280\n"));

        assertTrue(message.contains("'ui.viewport.width'") && message.contains("nest the levels"), message);
    }

    @Test
    void aKeyThatIsAValueInOneFileAndASectionInTheOtherFails() {
        assertEquals("'ui.viewport' is a value in test-automation.yaml and a section in test-automation-qa.yaml",
                message(() -> Config.of("qa", "ui:\n  viewport: big\n", "ui:\n  viewport:\n    width: 1\n")));
    }

    @Test
    void aListWhereAValueIsExpectedAndAValueWhereAListIsExpectedFail() {
        Config config = Config.of("qa", null, "ui:\n  browser: [a, b]\nmobile:\n  android:\n    devices: emulator-5554\n");

        assertTrue(message(() -> config.section("ui").find("browser")).contains("is a list"));
        assertTrue(message(() -> config.section("mobile.android").list("devices")).contains("is a single value"));
    }

    @Test
    void aListOfMapsFails() {
        assertTrue(message(() -> Config.of("qa", null, "mobile:\n  android:\n    devices:\n      - serial: a\n"))
                .contains("list of lists or maps"));
    }

    @Test
    void anEmptyFileHoldsNothing() {
        assertTrue(Config.of("qa", "", "# nothing yet\n").section("ui").isEmpty());
    }

    @Test
    void aSecretIsTheEnvironmentVariableTheFileNames() {
        Config config = Config.of("qa", null, "services:\n  payments:\n    secrets:\n      api-token: PAYMENTS_QA_TOKEN\n");
        Section payments = config.section("services.payments");

        assertEquals("s3cret", payments.secret("api-token", Map.of("PAYMENTS_QA_TOKEN", "s3cret")));

        String unset = message(() -> payments.secret("api-token", Map.of()));
        assertEquals("Secret 'api-token' needs environment variable PAYMENTS_QA_TOKEN, which is not set (blank"
                + " values count as missing; test-automation-qa.yaml names it)", unset);
        String unnamed = message(() -> payments.secret("user", Map.of("PAYMENTS_QA_TOKEN", "s3cret")));
        assertTrue(unnamed.startsWith("Missing 'services.payments.secrets.user' in test-automation-qa.yaml"));
        assertFalse(unset.contains("s3cret"));
    }

    @Test
    void aSecretNeverAppearsInAMessage() {
        Config config = Config.of("qa", null, "ui:\n  secrets:\n    pw: PW\n");

        assertFalse(message(() -> config.section("ui").secret("pw", Map.of("PW", " "))).contains("secret-value"));
    }
}
