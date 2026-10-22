package com.company.qa.ui;

import com.microsoft.playwright.Locator;

import java.util.Objects;

/**
 * Base of a consumer's component: a part of a screen that repeats or stands on its own (a header, a modal, a
 * table row). Page objects create it from the locator of its root; it looks for everything within that root.
 */
public abstract class BaseComponent {

    private final Locator root;

    protected BaseComponent(Locator root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    /** The component's root element; locate within it ({@code root().getByRole(...)}). */
    protected Locator root() {
        return root;
    }
}
