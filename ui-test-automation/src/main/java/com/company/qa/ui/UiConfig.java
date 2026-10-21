package com.company.qa.ui;

/** The settings of one test method's browser context, resolved from every source. */
final class UiConfig {

    private final UiConfigLoader loader;

    UiConfig(UiConfigLoader loader) {
        this.loader = loader;
    }

    /** Resolved when a page needs it, so a run that never opens a page of an app needs no URL for it. */
    String appUrl(String app, String pageName) {
        return loader.appUrl(app, pageName);
    }
}
