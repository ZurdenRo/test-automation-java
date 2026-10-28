package com.company.qa.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.schema.FailsafeSchema;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The settings of one environment: {@code test-automation.yaml} (optional, what every environment shares) with
 * {@code test-automation-<env>.yaml} (required) laid over it, key by key. A list is replaced whole; a blank or
 * {@code null} value counts as absent, so it never hides a value of the shared file.
 *
 * <p>Values are read as text, so {@code timeout-ms: 30000} and {@code timeout-ms: "30000"} are the same, and
 * {@code trace: off} is the text {@code off}, not a boolean. Keys are nested YAML, written in kebab-case; a key holds
 * no dot, which separates the levels of a {@link Section}'s path. Duplicate keys, sections that no module reads and
 * values with another shape than a scalar or a list of scalars are errors.
 */
public final class Config {

    public static final String COMMON_FILE = "test-automation.yaml";
    public static final String DEFAULT_ENV = "qa";

    private static final Set<String> SECTIONS = Set.of("ui", "services", "mobile");
    private static final Pattern ENV_NAME = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Logger LOG = LoggerFactory.getLogger(Config.class);

    private final String environment;
    private final List<String> files;
    private final Map<String, Entry> entries;

    private Config(String environment, List<String> files, Map<String, Entry> entries) {
        this.environment = environment;
        this.files = Collections.unmodifiableList(files);
        this.entries = entries;
    }

    /** The file that names an environment's own settings. */
    public static String environmentFile(String environment) {
        return "test-automation-" + environment + ".yaml";
    }

    /**
     * Reads the files for {@code environment} from the classpath.
     *
     * @param origin where the environment's name came from, for the error when its file is missing, e.g.
     *               {@code for <parameter> env 'staging'}; null when it needs no explaining
     */
    public static Config load(String environment, String origin) {
        if (!ENV_NAME.matcher(environment).matches()) {
            throw new ConfigException("Environment '" + environment + "' is not a name: use letters, digits, '-' and"
                    + " '_', since it is part of the file name " + environmentFile("<env>"));
        }
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = Config.class.getClassLoader();
        }
        Map<String, Entry> merged = new TreeMap<>();
        List<String> files = new ArrayList<>();

        String common = readResource(loader, COMMON_FILE);
        if (common != null) {
            overlay(merged, parse(COMMON_FILE, common));
            files.add(COMMON_FILE);
        }
        String file = environmentFile(environment);
        String own = readResource(loader, file);
        if (own == null) {
            throw new ConfigException("Config file '" + file + "'" + (origin == null ? "" : " (" + origin + ")")
                    + " not found on the classpath");
        }
        overlay(merged, parse(file, own));
        files.add(file);

        checkShapes(merged);
        Config config = new Config(environment, files, merged);
        LOG.debug("Config for env '{}' from {}", environment, files);
        return config;
    }

    /** Parses one file's text; for tests of this module and of the ones that read it. */
    static Config of(String environment, String commonYaml, String environmentYaml) {
        Map<String, Entry> merged = new TreeMap<>();
        List<String> files = new ArrayList<>();
        if (commonYaml != null) {
            overlay(merged, parse(COMMON_FILE, commonYaml));
            files.add(COMMON_FILE);
        }
        overlay(merged, parse(environmentFile(environment), environmentYaml));
        files.add(environmentFile(environment));
        checkShapes(merged);
        return new Config(environment, files, merged);
    }

    public String environment() {
        return environment;
    }

    /** The files read, the shared one first: what a run logs as the config it used. */
    public List<String> files() {
        return files;
    }

    /** The settings under {@code path} (e.g. {@code ui}, {@code services.users}); empty when there are none. */
    public Section section(String path) {
        return new Section(this, path);
    }

    Map<String, Entry> entries() {
        return entries;
    }

    private static String readResource(ClassLoader loader, String name) {
        try (InputStream in = loader.getResourceAsStream(name)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ConfigException("Could not read config file '" + name + "': " + e.getMessage(), e);
        }
    }

    private static Map<String, Entry> parse(String file, String yaml) {
        Map<String, Entry> flat = new TreeMap<>();
        if (yaml.lines().allMatch(line -> line.trim().isEmpty() || line.trim().startsWith("#"))) {
            return flat;
        }
        Object root;
        try {
            root = new Load(LoadSettings.builder()
                    .setSchema(new FailsafeSchema())
                    .setAllowDuplicateKeys(false)
                    .setTagConstructors(Map.of(Tag.NULL, node -> null))
                    .setLabel(file)
                    .build()).loadFromString(yaml);
        } catch (YamlEngineException e) {
            throw new ConfigException("Config file '" + file + "' is not valid YAML: " + firstLine(e), e);
        }
        if (root == null || root instanceof String && ((String) root).trim().isEmpty()) {
            return flat;
        }
        if (!(root instanceof Map)) {
            throw new ConfigException("Config file '" + file + "' must hold sections (ui, services, mobile) as"
                    + " nested keys");
        }
        flatten("", (Map<?, ?>) root, file, flat);
        for (String key : flat.keySet()) {
            String top = key.contains(".") ? key.substring(0, key.indexOf('.')) : key;
            if (!SECTIONS.contains(top)) {
                throw new ConfigException("Unknown section '" + top + "' in " + file + ": expected ui, services or mobile");
            }
        }
        return flat;
    }

    private static void flatten(String prefix, Map<?, ?> map, String file, Map<String, Entry> out) {
        for (Map.Entry<?, ?> each : map.entrySet()) {
            if (!(each.getKey() instanceof String)) {
                throw new ConfigException("Key " + each.getKey() + " in " + file + " is not text");
            }
            String name = ((String) each.getKey()).trim();
            if (name.isEmpty() || name.contains(".")) {
                throw new ConfigException("Key '" + prefix + name + "' in " + file + " is empty or holds a dot:"
                        + " nest the levels instead (a dot separates them in a key's path)");
            }
            String key = prefix + name;
            Object value = each.getValue();
            if (value instanceof Map) {
                flatten(key + ".", (Map<?, ?>) value, file, out);
            } else if (value instanceof List) {
                List<String> items = new ArrayList<>();
                for (Object item : (List<?>) value) {
                    if (!(item instanceof String)) {
                        throw new ConfigException("'" + key + "' in " + file + " is a list of lists or maps:"
                                + " expected a list of plain values");
                    }
                    items.add(((String) item).trim());
                }
                out.put(key, Entry.list(items, file));
            } else if (isPresent((String) value)) {
                out.put(key, Entry.scalar(((String) value).trim(), file));
            }
        }
    }

    /** The environment's file over the shared one: a key it sets replaces the shared key's value. */
    private static void overlay(Map<String, Entry> base, Map<String, Entry> top) {
        base.putAll(top);
    }

    /** A key cannot be a value in one file and a section in the other: {@code ui.viewport} and {@code ui.viewport.width}. */
    private static void checkShapes(Map<String, Entry> merged) {
        String previous = null;
        for (String key : merged.keySet()) {
            if (previous != null && key.startsWith(previous + ".")) {
                throw new ConfigException("'" + previous + "' is a value in " + merged.get(previous).source
                        + " and a section in " + merged.get(key).source);
            }
            previous = key;
        }
    }

    static boolean isPresent(String value) {
        if (value == null) {
            return false;
        }
        String text = value.trim();
        return !text.isEmpty() && !text.equals("null") && !text.equals("~");
    }

    private static String firstLine(Exception e) {
        String message = e.getMessage() == null ? e.toString() : e.getMessage();
        int end = message.indexOf('\n');
        return end < 0 ? message : message.substring(0, end);
    }

    /** A value or a list of values with the file it was written in. */
    static final class Entry {
        final String scalar;
        final List<String> list;
        final String source;

        private Entry(String scalar, List<String> list, String source) {
            this.scalar = scalar;
            this.list = list;
            this.source = source;
        }

        static Entry scalar(String value, String source) {
            return new Entry(value, null, source);
        }

        static Entry list(List<String> items, String source) {
            return new Entry(null, Collections.unmodifiableList(items), source);
        }
    }
}
