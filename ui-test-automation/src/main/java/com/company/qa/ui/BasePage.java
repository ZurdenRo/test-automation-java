package com.company.qa.ui;

import com.microsoft.playwright.Page;

import java.util.Objects;

/**
 * Base of a consumer's page object: one screen of the app under test, exposing what a user can do there.
 * The browser page is protected so that tests call {@code login.loginAs(user)}, not Playwright.
 * Keep the constructor to {@code super(page)}: the library creates page objects to read their path and app.
 */
public abstract class BasePage {

    private final Page page;

    protected BasePage(Page page) {
        this.page = Objects.requireNonNull(page, "page");
    }

    /** The tab this page object is bound to; it always acts there. */
    protected Page page() {
        return page;
    }

    /**
     * Where this page lives within its app, relative to the app's base URL, with named parameters in braces
     * ({@code "/users/{id}"}). Null, the default, for a page reached only from another one.
     */
    protected String path() {
        return null;
    }

    /**
     * The app this page lives in: its base URL is {@code app.<name>.url}. Every page with a {@link #path()} must
     * name one; a page without a path never navigates, so it needs none. A consumer whose pages share an app
     * overrides it once, in its own base page.
     */
    protected String app() {
        return null;
    }
}
