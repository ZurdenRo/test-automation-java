package com.company.qa.ui;

import com.microsoft.playwright.Page;

import java.util.Objects;

/**
 * Base of a consumer's page object: one screen of the app under test, exposing what a user can do there.
 * The browser page is protected so that tests call {@code login.loginAs(user)}, not Playwright.
 * Keep the constructor to {@code super(page)}: the library creates page objects to read their path and app, and
 * hands them what {@link #secret(String)} needs once they are created.
 */
public abstract class BasePage {

    private final Page page;
    private UiContext uiContext;

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
     * The app this page lives in: its base URL is {@code ui.apps.<name>.url}. Every page with a {@link #path()} must
     * name one; a page without a path never navigates, so it needs none. A consumer whose pages share an app
     * overrides it once, in its own base page.
     */
    protected String app() {
        return null;
    }

    /**
     * A secret of this page's app (a user, a password, a key) by name: {@code secret("password")} reads the
     * environment variable that {@code ui.apps.<app>.secrets.password} names in the environment's config file, so
     * the page is the same in every environment. Use it to fill a field; never log it.
     *
     * @throws IllegalStateException if the page names no {@link #app()}, or was created with {@code new} instead of
     *                               by {@code open(...)} or {@code on(...)}
     */
    protected String secret(String name) {
        String app = app();
        if (app == null) {
            throw new IllegalStateException(getClass().getSimpleName() + " names no app: override app() so that"
                    + " secret(\"" + name + "\") knows whose ui.apps.<app>.secrets to read");
        }
        if (uiContext == null) {
            throw new IllegalStateException(getClass().getSimpleName() + " was created with new: create page objects"
                    + " with open(...) or on(...) so that secret(\"" + name + "\") can reach the config");
        }
        return uiContext.secret(app, name);
    }

    /** Called by the library when it creates the page object. */
    void bind(UiContext uiContext) {
        this.uiContext = uiContext;
    }
}
