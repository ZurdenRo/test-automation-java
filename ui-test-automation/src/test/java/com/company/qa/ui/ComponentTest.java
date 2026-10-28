package com.company.qa.ui;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

class ComponentTest {

    private static TestSite site;

    @TempDir
    Path classpathDir;

    @BeforeAll
    static void startSite() {
        site = new TestSite("appA").page("/orders", "<table>"
                + "<tr><td class='id'>A-1</td><td class='state'>open</td>"
                + "<td><button onclick=\"this.closest('tr').querySelector('.state').textContent='closed'\">Close"
                + "</button></td></tr>"
                + "<tr><td class='id'>A-2</td><td class='state'>open</td>"
                + "<td><button onclick=\"this.closest('tr').querySelector('.state').textContent='closed'\">Close"
                + "</button></td></tr>"
                + "</table>");
    }

    @AfterAll
    static void stopSite() {
        site.close();
    }

    @Test
    void aComponentActsOnlyWithinItsRoot() {
        ConsumerRun.in(classpathDir).config("apps.shop", site.url())
                .run(ClosesTheSecondOrderTest.class)
                .assertAllPassed();
    }

    public static class OrderRow extends BaseComponent {
        public OrderRow(Locator root) {
            super(root);
        }

        public void close() {
            root().getByText("Close").click();
        }

        public Locator state() {
            return root().locator(".state");
        }
    }

    public static class OrdersPage extends ShopPage {
        public OrdersPage(Page page) {
            super(page);
        }

        @Override
        protected String path() {
            return "/orders";
        }

        public OrderRow order(String id) {
            return new OrderRow(page().locator("tr").filter(new Locator.FilterOptions().setHasText(id)));
        }
    }

    public static class ClosesTheSecondOrderTest extends BaseUiTest {
        @org.testng.annotations.Test
        public void closesTheSecondOrder() {
            OrdersPage orders = open(OrdersPage.class);
            orders.order("A-2").close();
            assertThat(orders.order("A-2").state()).hasText("closed");
            assertThat(orders.order("A-1").state()).hasText("open");
        }
    }
}
