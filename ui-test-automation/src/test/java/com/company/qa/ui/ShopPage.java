package com.company.qa.ui;

import com.microsoft.playwright.Page;

/** The consumer's own base for the pages of one app, so each page object does not repeat {@code app()}. */
public abstract class ShopPage extends BasePage {

    protected ShopPage(Page page) {
        super(page);
    }

    @Override
    protected String app() {
        return "shop";
    }
}
