package com.company.qa.ui;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PathParametersTest {

    private static TestSite site;

    @TempDir
    Path classpathDir;

    @BeforeAll
    static void startSite() {
        site = new TestSite("appA");
    }

    @AfterAll
    static void stopSite() {
        site.close();
    }

    @Test
    void namedParametersFillThePathWhateverTheirOrder() {
        run(OpensOrder.class).assertAllPassed();
    }

    @Test
    void parameterValuesAreEncodedAsPathSegments() {
        run(OpensOrderWithSpaces.class).assertAllPassed();
    }

    @Test
    void aMissingParameterIsNamed() {
        assertEquals("OrderPage path '/users/{id}/orders/{orderId}' needs parameter 'orderId'",
                run(MissesAParameter.class).failure("missesAParameter").getMessage());
    }

    @Test
    void anUnknownParameterIsNamed() {
        assertEquals("OrderPage path '/users/{id}/orders/{orderId}' has no parameter 'userId'",
                run(GivesAnUnknownParameter.class).failure("givesAnUnknownParameter").getMessage());
    }

    @Test
    void openingAPathWithParametersWithoutThemSaysToUseTo() {
        assertEquals("OrderPage path '/users/{id}/orders/{orderId}' has parameters: use to(OrderPage.class)"
                        + ".with(name, value).open()",
                run(OpensWithoutParameters.class).failure("opensWithoutParameters").getMessage());
    }

    private ConsumerRun.Results run(Class<?> test) {
        return ConsumerRun.in(classpathDir).config("base.url", site.url()).run(test);
    }

    public static class OrderPage extends BasePage {
        public OrderPage(Page page) {
            super(page);
        }

        @Override
        protected String path() {
            return "/users/{id}/orders/{orderId}";
        }

        public Locator shownPath() {
            return page().locator("#path");
        }
    }

    public static class OpensOrder extends BaseUiTest {
        @org.testng.annotations.Test
        public void opensOrder() {
            OrderPage order = to(OrderPage.class).with("orderId", 42).with("id", 7).open();
            assertThat(order.shownPath()).hasText("/users/7/orders/42");
        }
    }

    public static class OpensOrderWithSpaces extends BaseUiTest {
        @org.testng.annotations.Test
        public void opensOrderWithSpaces() {
            OrderPage order = to(OrderPage.class).with("id", "ana maria").with("orderId", "a/b").open();
            assertThat(order.shownPath()).hasText("/users/ana%20maria/orders/a%2Fb");
        }
    }

    public static class MissesAParameter extends BaseUiTest {
        @org.testng.annotations.Test
        public void missesAParameter() {
            to(OrderPage.class).with("id", 7).open();
        }
    }

    public static class GivesAnUnknownParameter extends BaseUiTest {
        @org.testng.annotations.Test
        public void givesAnUnknownParameter() {
            to(OrderPage.class).with("id", 7).with("orderId", 42).with("userId", 1).open();
        }
    }

    public static class OpensWithoutParameters extends BaseUiTest {
        @org.testng.annotations.Test
        public void opensWithoutParameters() {
            open(OrderPage.class);
        }
    }
}
