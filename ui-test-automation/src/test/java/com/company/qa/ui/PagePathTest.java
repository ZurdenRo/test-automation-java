package com.company.qa.ui;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PagePathTest {

    @Test
    void oneSlashJoinsTheAppUrlAndThePathHoweverEitherIsWritten() {
        assertEquals("https://shop/cart", PagePath.url("CartPage", "https://shop", "/cart", Map.of()));
        assertEquals("https://shop/cart", PagePath.url("CartPage", "https://shop/", "/cart", Map.of()));
        assertEquals("https://shop/cart", PagePath.url("CartPage", "https://shop", "cart", Map.of()));
        assertEquals("https://shop/cart", PagePath.url("CartPage", "https://shop/", "cart", Map.of()));
    }

    @Test
    void anAppUrlWithAPathOfItsOwnKeepsIt() {
        assertEquals("https://host/shop/cart", PagePath.url("CartPage", "https://host/shop/", "/cart", Map.of()));
    }

    @Test
    void theRootPathIsTheAppUrlWithASlash() {
        assertEquals("https://shop/", PagePath.url("HomePage", "https://shop", "/", Map.of()));
    }

    @Test
    void parametersAreFilledBeforeJoining() {
        assertEquals("https://shop/orders/7/lines/a%20b", PagePath.url("LinePage", "https://shop/",
                "orders/{id}/lines/{line}", Map.of("line", "a b", "id", "7")));
    }
}
