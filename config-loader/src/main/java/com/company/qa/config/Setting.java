package com.company.qa.config;

import java.util.List;
import java.util.Locale;

/** One value with the key it was read under and where it came from, so that an error can name both. */
public final class Setting {

    private final String key;
    private final String value;
    private final String source;

    /** @param source what to call where the value came from: a file name, or e.g. {@code <parameter> browser} */
    public static Setting of(String key, String value, String source) {
        return new Setting(key, value, source);
    }

    private Setting(String key, String value, String source) {
        this.key = key;
        this.value = value.trim();
        this.source = source;
    }

    /** The full key, e.g. {@code ui.viewport.width}. */
    public String key() {
        return key;
    }

    public String value() {
        return value;
    }

    public String source() {
        return source;
    }

    public int positiveInt() {
        return wholeNumber(1, "a positive whole number");
    }

    public int nonNegativeInt() {
        return wholeNumber(0, "zero or a positive whole number");
    }

    public boolean bool() {
        String text = value.toLowerCase(Locale.ROOT);
        if (!text.equals("true") && !text.equals("false")) {
            throw invalid("true or false");
        }
        return Boolean.parseBoolean(text);
    }

    /** The value in lower case, one of {@code allowed}. */
    public String oneOf(List<String> allowed) {
        String text = value.toLowerCase(Locale.ROOT);
        if (!allowed.contains(text)) {
            String last = allowed.get(allowed.size() - 1);
            String expected = allowed.size() == 1 ? last
                    : String.join(", ", allowed.subList(0, allowed.size() - 1)) + " or " + last;
            throw invalid(expected);
        }
        return text;
    }

    /** A message for a value this setting holds that its reader rejects. */
    public ConfigException invalid(String expected) {
        return new ConfigException("'" + key + "' in " + source + " is '" + value + "': expected " + expected);
    }

    private int wholeNumber(int minimum, String expected) {
        try {
            int number = Integer.parseInt(value);
            if (number >= minimum) {
                return number;
            }
        } catch (NumberFormatException ignored) {
            // reported below, like a number out of range
        }
        throw invalid(expected);
    }
}
