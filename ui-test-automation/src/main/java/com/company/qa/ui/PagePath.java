package com.company.qa.ui;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A page object's URL: its path, with its named parameters ({@code "/users/{id}"}) filled in, under its app's. */
final class PagePath {

    private static final Pattern PARAMETER = Pattern.compile("\\{([^}/]+)}");

    private PagePath() {
    }

    /**
     * @param pageName for the messages
     * @param baseUrl  the app's URL; a path of its own ({@code https://host/shop}) is kept
     * @param values   every parameter of the template, and none it lacks; none for a template without any
     */
    static String url(String pageName, String baseUrl, String template, Map<String, String> values) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String path = fill(pageName, template, values);
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    private static String fill(String pageName, String template, Map<String, String> values) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = PARAMETER.matcher(template);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        if (values.isEmpty() && !names.isEmpty()) {
            throw new IllegalStateException(pageName + " path '" + template + "' has parameters: use to("
                    + pageName + ".class).with(name, value).open()");
        }
        for (String given : values.keySet()) {
            if (!names.contains(given)) {
                throw new IllegalArgumentException(pageName + " path '" + template + "' has no parameter '"
                        + given + "'");
            }
        }
        StringBuilder path = new StringBuilder();
        matcher.reset();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = values.get(name);
            if (value == null) {
                throw new IllegalArgumentException(pageName + " path '" + template + "' needs parameter '"
                        + name + "'");
            }
            matcher.appendReplacement(path, Matcher.quoteReplacement(encodeSegment(value)));
        }
        matcher.appendTail(path);
        return path.toString();
    }

    private static String encodeSegment(String value) {
        // URLEncoder encodes for forms: a space becomes '+', which in a path is a literal plus.
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
