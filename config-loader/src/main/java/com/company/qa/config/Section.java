package com.company.qa.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/** The settings under one key of a {@link Config}, such as {@code ui} or {@code services.users}. */
public final class Section {

    private final Config config;
    private final String path;

    Section(Config config, String path) {
        this.config = config;
        this.path = path;
    }

    public String path() {
        return path;
    }

    /** True when the files hold no setting under this section. */
    public boolean isEmpty() {
        return names().isEmpty();
    }

    /** The settings under {@code name}, relative to this section. */
    public Section section(String name) {
        return new Section(config, path + "." + name);
    }

    /** The first-level names under this section: the services of {@code services}, the apps of {@code ui.apps}. */
    public SortedSet<String> names() {
        SortedSet<String> names = new TreeSet<>();
        String prefix = path + ".";
        for (String key : config.entries().keySet()) {
            if (key.startsWith(prefix)) {
                String rest = key.substring(prefix.length());
                names.add(rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : rest);
            }
        }
        return names;
    }

    /** The value at {@code key} (a dotted path relative to this section), unless absent. */
    public Optional<Setting> find(String key) {
        Config.Entry entry = config.entries().get(path + "." + key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.scalar == null) {
            throw new ConfigException("'" + path + "." + key + "' in " + entry.source
                    + " is a list: expected a single value");
        }
        return Optional.of(Setting.of(path + "." + key, entry.scalar, entry.source));
    }

    /** @param hint what the value is for, appended to the error; may be empty */
    public Setting require(String key, String hint) {
        return find(key).orElseThrow(() -> new ConfigException("Missing '" + path + "." + key + "' in "
                + sources() + hint + " (blank values count as missing)"));
    }

    /** The list at {@code key}, unless absent. */
    public Optional<List<Setting>> list(String key) {
        Config.Entry entry = config.entries().get(path + "." + key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.list == null) {
            throw new ConfigException("'" + path + "." + key + "' in " + entry.source
                    + " is a single value: expected a list");
        }
        List<Setting> settings = new ArrayList<>();
        for (String item : entry.list) {
            settings.add(Setting.of(path + "." + key, item, entry.source));
        }
        return Optional.of(Collections.unmodifiableList(settings));
    }

    /**
     * Fails on a key under this section that is not one the reader knows, so that {@code heedless: true} is an
     * error and not a setting that quietly does nothing.
     *
     * @param keys     the keys read, relative to this section: {@code browser}, {@code viewport.width}
     * @param prefixes the keys under which any name is allowed: {@code apps}, {@code secrets}, {@code headers}
     */
    public void rejectUnknown(Collection<String> keys, Collection<String> prefixes) {
        String start = path + ".";
        for (Map.Entry<String, Config.Entry> each : config.entries().entrySet()) {
            if (!each.getKey().startsWith(start)) {
                continue;
            }
            String relative = each.getKey().substring(start.length());
            if (!keys.contains(relative) && prefixes.stream().noneMatch(p -> relative.startsWith(p + "."))) {
                throw new ConfigException("Unknown key '" + each.getKey() + "' in " + each.getValue().source
                        + ": expected one of " + String.join(", ", keys)
                        + (prefixes.isEmpty() ? "" : ", or a name under " + String.join(", ", prefixes)));
            }
        }
    }

    /**
     * A secret, read from the environment variable that {@code secrets.<name>} names, so a test asks for the same
     * name in every environment. The value never appears in a message.
     *
     * @param env where secrets are read from, and nothing else
     */
    public String secret(String name, Map<String, String> env) {
        String key = "secrets." + name;
        Setting variable = find(key).orElseThrow(() -> new ConfigException("Missing '" + path + "." + key + "' in "
                + sources() + ": it names the environment variable that holds secret '" + name + "'"));
        String value = env.get(variable.value());
        if (value == null || value.trim().isEmpty()) {
            throw new ConfigException("Secret '" + name + "' needs environment variable " + variable.value()
                    + ", which is not set (blank values count as missing; " + variable.source() + " names it)");
        }
        return value;
    }

    private String sources() {
        return String.join(" or ", config.files());
    }
}
