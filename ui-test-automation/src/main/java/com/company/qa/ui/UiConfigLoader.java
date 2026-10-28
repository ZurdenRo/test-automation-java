package com.company.qa.ui;

import com.company.qa.config.Config;
import com.company.qa.config.ConfigException;
import com.company.qa.config.Section;
import com.company.qa.config.Setting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Resolves each setting from, in order: a {@code <parameter>} of the TestNG {@code <test>} (only for the settings
 * that make sense to vary per {@code <test>}), the {@code ui} section of the config, a default. The config is
 * {@code test-automation-<env>.yaml} over {@code test-automation.yaml} (root docs/adr/0005). Remembers where each
 * value came from so that errors can say so. Environment variables are not a source: they hold secrets only.
 */
final class UiConfigLoader {

    static final String BROWSER = "browser";
    static final String CHANNEL = "channel";
    static final String HEADLESS = "headless";
    static final String TIMEOUT_MS = "timeout-ms";
    static final String VIEWPORT_WIDTH = "viewport.width";
    static final String VIEWPORT_HEIGHT = "viewport.height";
    static final String SLOW_MO_MS = "slow-mo-ms";
    static final String EVIDENCE_DIR = "evidence-dir";
    static final String TRACE = "trace";
    private static final String APPS = "apps";
    private static final String URL = "url";
    private static final String SECRETS = "secrets";

    static final String ENV = "env";
    static final String DEFAULT_ENV = Config.DEFAULT_ENV;
    private static final String RETIRED_CONFIG = "ui.config";
    private static final Logger LOG = LoggerFactory.getLogger(UiConfigLoader.class);
    private static final Set<String> XML_SETTINGS = Set.of(BROWSER, CHANNEL, HEADLESS, VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
    private static final List<String> KEYS = List.of(BROWSER, CHANNEL, HEADLESS, TIMEOUT_MS, VIEWPORT_WIDTH,
            VIEWPORT_HEIGHT, SLOW_MO_MS, EVIDENCE_DIR, TRACE);
    private static final List<String> OPEN_PREFIXES = List.of(APPS);
    private static final List<String> APP_KEYS = List.of(URL);
    private static final List<String> APP_OPEN_PREFIXES = List.of(SECRETS);
    private static final List<String> BROWSERS = List.of("chromium", "firefox", "webkit");
    private static final List<String> CHANNELS = List.of("chrome", "msedge");
    private static final List<String> TRACE_MODES = List.of("off", "on-failure");

    private final Section ui;
    private final Map<String, String> xmlParameters;
    private final Map<String, String> env;

    private UiConfigLoader(Section ui, Map<String, String> xmlParameters, Map<String, String> env) {
        this.ui = ui;
        this.xmlParameters = xmlParameters;
        this.env = env;
    }

    /**
     * @param xmlParameters the {@code <parameter>}s of the TestNG {@code <test>} running, its suite's included;
     *                      {@code env} among them names the suite's environment, whose settings are in
     *                      {@code test-automation-<env>.yaml}
     */
    static UiConfig load(Map<String, String> xmlParameters) {
        return load(xmlParameters, System.getenv());
    }

    /** @param env where secrets are read from, and nothing else */
    static UiConfig load(Map<String, String> xmlParameters, Map<String, String> env) {
        String environment = environment(xmlParameters);
        String origin = isPresent(xmlParameters.get(ENV)) ? "for <parameter> " + ENV + " '" + environment + "'"
                : "for env '" + environment + "', the default; name another with <parameter> " + ENV;
        Config config = translated(() -> Config.load(environment, origin));
        Section ui = config.section("ui");
        translated(() -> {
            ui.rejectUnknown(KEYS, OPEN_PREFIXES);
            for (String app : ui.section(APPS).names()) {
                ui.find(APPS + "." + app).ifPresent(value -> {
                    throw new ConfigException("'" + ui.path() + "." + APPS + "." + app + "' in " + value.source()
                            + " is a value: an app is a section with '" + URL + "' and, optionally, '" + SECRETS + "'");
                });
                ui.section(APPS + "." + app).rejectUnknown(APP_KEYS, APP_OPEN_PREFIXES);
            }
            return null;
        });
        UiConfigLoader loader = new UiConfigLoader(ui, xmlParameters, env);
        UiConfig result = translated(() -> new UiConfig(loader, environment,
                loader.oneOf(BROWSER, BROWSERS, "chromium"),
                loader.oneOf(CHANNEL, CHANNELS, null),
                loader.find(HEADLESS).map(Setting::bool).orElse(true),
                loader.find(TIMEOUT_MS).map(Setting::positiveInt).orElse(30_000),
                loader.find(VIEWPORT_WIDTH).map(Setting::positiveInt).orElse(1280),
                loader.find(VIEWPORT_HEIGHT).map(Setting::positiveInt).orElse(720),
                loader.find(SLOW_MO_MS).map(Setting::nonNegativeInt).orElse(0),
                Path.of(loader.find(EVIDENCE_DIR).map(Setting::value).orElse("ui-evidence")),
                "on-failure".equals(loader.oneOf(TRACE, TRACE_MODES, "off"))));
        LOG.debug("Settings from {}: browser={}, channel={}, headless={}, viewport={}x{}, timeout-ms={},"
                        + " slow-mo-ms={}, evidence-dir={}, trace={}", config.files(), result.browser(),
                result.channel(), result.headless(), result.viewportWidth(), result.viewportHeight(),
                result.timeoutMs(), result.slowMoMs(), result.evidenceDir(),
                result.traceOnFailure() ? "on-failure" : "off");
        return result;
    }

    /**
     * Logs the environment a TestNG {@code <test>} runs on and the files its config is read from, once, and warns
     * about {@code ui.config}, which {@code env} replaced: a suite still setting it would otherwise run on the
     * default environment unnoticed. A config that cannot be read is reported when a test needs it, not here.
     */
    static void logEnvironment(String testName, Map<String, String> xmlParameters) {
        String environment = environment(xmlParameters);
        String files;
        try {
            files = String.join(" + ", Config.load(environment, null).files());
        } catch (ConfigException e) {
            files = Config.environmentFile(environment);
        }
        String retired = xmlParameters.get(RETIRED_CONFIG);
        if (isPresent(retired)) {
            LOG.warn("Ignoring <parameter> {} '{}': <parameter> {} replaced it, so <test> '{}' runs on env '{}' ({})",
                    RETIRED_CONFIG, retired.trim(), ENV, testName, environment, files);
        }
        LOG.info("<test> '{}' runs on env '{}' ({})", testName, environment, files);
    }

    private static String environment(Map<String, String> xmlParameters) {
        String named = xmlParameters.get(ENV);
        return isPresent(named) ? named.trim() : DEFAULT_ENV;
    }

    /** The base URL of the named app ({@code ui.apps.<name>.url}), for the page that needs it. */
    String appUrl(String app, String pageName) {
        return translated(() -> ui.require(APPS + "." + app + "." + URL, ", needed by " + pageName).value());
    }

    /**
     * A secret of an app, read from the environment variable that {@code ui.apps.<app>.secrets.<name>} names in the
     * environment's file, so a test asks for the same name in every environment. The value never appears in a
     * message.
     */
    String secret(String app, String name) {
        Section section = ui.section(APPS + "." + app);
        String value = translated(() -> section.secret(name, env));
        LOG.debug("Secret '{}' of app '{}' read from environment variable {}", name, app,
                section.find(SECRETS + "." + name).map(Setting::value).orElse("?"));
        return value;
    }

    private Optional<Setting> find(String key) {
        if (XML_SETTINGS.contains(key)) {
            String fromXml = xmlParameters.get(key);
            if (isPresent(fromXml)) {
                return Optional.of(Setting.of(key, fromXml, "<parameter> " + key));
            }
        }
        return ui.find(key);
    }

    private String oneOf(String key, List<String> allowed, String fallback) {
        return find(key).map(setting -> setting.oneOf(allowed)).orElse(fallback);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static <T> T translated(Supplier<T> action) {
        try {
            return action.get();
        } catch (ConfigException e) {
            throw new UiConfigException(e.getMessage(), e);
        }
    }
}
