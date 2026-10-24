package com.company.qa.ui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A pending navigation to a page object whose path has named parameters:
 * {@code to(OrderPage.class).with("id", 7).with("orderId", 42).open()}. Nothing happens until {@link #open()}.
 */
public final class PageNavigation<T extends BasePage> {

    private final Class<T> type;
    private final UiContext context;
    private final Map<String, String> parameters = new LinkedHashMap<>();

    PageNavigation(Class<T> type, UiContext context) {
        this.type = type;
        this.context = context;
    }

    /** The value of the path parameter {@code {name}}, as its {@code toString()}, encoded as a path segment. */
    public PageNavigation<T> with(String name, Object value) {
        parameters.put(Objects.requireNonNull(name, "name"), String.valueOf(Objects.requireNonNull(value, name)));
        return this;
    }

    /** Navigates the test's tab to the filled path and returns the page object. */
    public T open() {
        return context.open(type, Collections.unmodifiableMap(parameters), false);
    }

    /** Opens the filled path in a new tab and returns the page object, bound to that tab. */
    public T openInNewTab() {
        return context.open(type, Collections.unmodifiableMap(parameters), true);
    }
}
