package com.company.qa.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Immutable HTTP headers: names are case-insensitive and a name may carry several values. */
public final class Headers {

    private static final Headers EMPTY = new Headers(new LinkedHashMap<>());

    /** Keyed by lower-cased name; the first-seen casing is kept for display. */
    private final Map<String, HeaderValues> byName;

    private Headers(Map<String, HeaderValues> byName) {
        this.byName = byName;
    }

    public static Headers empty() {
        return EMPTY;
    }

    public static Headers of(String name, String value) {
        return EMPTY.and(name, value);
    }

    /** Returns a copy with one more value for {@code name}; existing values are kept. */
    public Headers and(String name, String value) {
        Map<String, HeaderValues> copy = new LinkedHashMap<>(byName);
        HeaderValues existing = copy.get(key(name));
        List<String> values = existing == null ? new ArrayList<>() : new ArrayList<>(existing.values);
        values.add(value);
        copy.put(key(name), new HeaderValues(existing == null ? name : existing.name, values));
        return new Headers(copy);
    }

    /** Returns a copy with every value of {@code more} added; existing values are kept. */
    Headers and(Headers more) {
        Headers result = this;
        for (Map.Entry<String, String> pair : more.pairs()) {
            result = result.and(pair.getKey(), pair.getValue());
        }
        return result;
    }

    public Optional<String> first(String name) {
        HeaderValues entry = byName.get(key(name));
        return entry == null ? Optional.empty() : Optional.of(entry.values.get(0));
    }

    public List<String> all(String name) {
        HeaderValues entry = byName.get(key(name));
        return entry == null ? List.of() : Collections.unmodifiableList(entry.values);
    }

    public boolean contains(String name) {
        return byName.containsKey(key(name));
    }

    /** Names with the casing the sender used. */
    public Set<String> names() {
        Set<String> names = new LinkedHashSet<>();
        for (HeaderValues entry : byName.values()) {
            names.add(entry.name);
        }
        return Collections.unmodifiableSet(names);
    }

    /** Headers of {@code other} replace, name by name, the ones here; the rest are kept. */
    Headers overriddenBy(Headers other) {
        Map<String, HeaderValues> merged = new LinkedHashMap<>(byName);
        merged.putAll(other.byName);
        return new Headers(merged);
    }

    /** Flattened to name/value pairs, one per value, for sending. */
    List<Map.Entry<String, String>> pairs() {
        List<Map.Entry<String, String>> pairs = new ArrayList<>();
        for (HeaderValues entry : byName.values()) {
            for (String value : entry.values) {
                pairs.add(Map.entry(entry.name, value));
            }
        }
        return pairs;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static final class HeaderValues {
        final String name;
        final List<String> values;

        HeaderValues(String name, List<String> values) {
            this.name = name;
            this.values = values;
        }
    }
}
