package com.company.qa.http;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Immutable, ordered query parameters. Encoding is done here so callers never build a query string by hand. */
public final class QueryParams {

    private static final QueryParams EMPTY = new QueryParams(List.of());

    private final List<Map.Entry<String, String>> pairs;

    private QueryParams(List<Map.Entry<String, String>> pairs) {
        this.pairs = pairs;
    }

    public static QueryParams empty() {
        return EMPTY;
    }

    public static QueryParams of(String name, String value) {
        return EMPTY.and(name, value);
    }

    /** Returns a copy with one more parameter; the same name may repeat. */
    public QueryParams and(String name, String value) {
        List<Map.Entry<String, String>> copy = new ArrayList<>(pairs);
        copy.add(Map.entry(name, value));
        return new QueryParams(copy);
    }

    boolean isEmpty() {
        return pairs.isEmpty();
    }

    String encoded() {
        return pairs.stream()
                .map(p -> encode(p.getKey()) + "=" + encode(p.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
