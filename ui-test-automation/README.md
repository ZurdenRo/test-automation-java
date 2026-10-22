# ui-test-automation

Drive web apps from TestNG tests through page objects, on [Playwright](https://playwright.dev/java/). You write a **page object** per screen and a **component** per repeating part; tests call their methods and never touch the browser page. Playwright is yours to use inside them.

## A page object

Every page with a path names the app it lives in. Pages of the same app share it through a base of your own:

```java
public abstract class ShopPage extends BasePage {

    protected ShopPage(Page page) {
        super(page);
    }

    @Override
    protected String app() {
        return "shop";                        // its base URL is app.shop.url
    }
}

public class LoginPage extends ShopPage {

    public LoginPage(Page page) {
        super(page);                          // nothing else here: the library creates page objects to read their path
    }

    @Override
    protected String path() {
        return "/login";                      // relative to the app's base URL
    }

    public DashboardPage loginAs(String user, String password) {
        page().getByLabel("User").fill(user);
        page().getByLabel("Password").fill(password);
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
        return new DashboardPage(page());
    }
}
```

A page reached only from another one (a dashboard after login) has no `path()`, and then needs no `app()` either: it never navigates. A path can have named parameters: `"/users/{id}/orders/{orderId}"`.

## A component

```java
public class OrderRow extends BaseComponent {

    public OrderRow(Locator root) {
        super(root);
    }

    public void close() {
        root().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Close")).click();
    }

    public Locator state() {
        return root().locator(".state");
    }
}

// in OrdersPage
public OrderRow order(String id) {
    return new OrderRow(page().locator("tr").filter(new Locator.FilterOptions().setHasText(id)));
}
```

Everything a component looks for, it looks for within its root.

## A UI test

```java
public class LoginTest extends BaseUiTest {

    @Test
    public void validUserSeesDashboard() {
        DashboardPage dashboard = open(LoginPage.class).loginAs("ana", "secret");
        assertThat(dashboard.greeting()).hasText("Hello, Ana");
    }
}
```

| Method | Does |
|---|---|
| `open(LoginPage.class)` | Navigates the test's tab to the page's path. How a test starts, or skips the steps it is not about. |
| `to(OrderPage.class).with("id", 7).with("orderId", 42).open()` | Same, for a path with parameters. Values are encoded as path segments. |
| `openInNewTab(InvoicePage.class)` | Opens the page in a new tab (also `to(...).openInNewTab()`). |
| `on(DashboardPage.class)` | The page object for the screen the tab shows now, without navigating: after a redirect, or a click whose destination the previous page cannot know. |

Each page object is bound to the tab it was created on, so using two tabs is using two page objects; there is no switching.

Each test method gets a fresh browser context (cookies, storage, tabs), so tests never depend on each other. Each thread launches its browser once, so `parallel="methods"` works without changes.

## Conventions

- **Return `Locator` for what the test asserts**, and assert with Playwright's `assertThat`: it retries until the page settles. A value read once (`textContent()`) makes a test flaky while the page loads. Return a value when it is already stable, like an id used in a later step.
- **What a page object returns is yours.** If where an action lands depends on the user, return nothing and let the test say where it expects to be with `on(...)`.

## Apps

Each app has a name and a base URL in the config; each page object with a path names its app with `app()`:

```properties
app.shop.url=https://shop.example.com
app.billing.url=https://billing.example.com
```

A test moves between apps by opening their pages, in one tab or in several. Since hosts live only in the config, switching environments moves them all. Opening a page with a path but no app fails, naming the page.

## Configuration

Settings live in one `.properties` file per environment on the test classpath (`src/test/resources`), named `ui-test-<env>.properties`. A suite names its environment with the `env` parameter; without it the environment is `qa`:

```xml
<suite name="checkout-staging">
  <parameter name="env" value="staging"/>   <!-- reads ui-test-staging.properties -->
  <test name="checkout"><classes>...</classes></test>
</suite>
```

So each suite xml points at one environment, and CI picks the environment by picking the xml.

A test that also calls services passes the same environment to their clients, so one parameter moves the whole test:

```java
public class UsersClient extends HttpServiceClient {
    public UsersClient(String env) {
        super("users-service-" + env + ".properties");
    }
}
```

```java
UsersClient users = new UsersClient(environment());
```

### Running from the IDE

Running a test or class with the IDE's run button works the same: the IDE builds a suite of its own, without parameters, so the test runs on `qa`. To run on another environment, add `env` = `staging` under **Parameters** in the TestNG run configuration, and save it to reuse. An IDE started from a desktop launcher does not see variables exported in a shell profile: set the secrets in the run configuration's environment variables, or start the IDE from a shell that has them.

For each setting, the first value found wins:

1. a `<parameter>` of the running `<test>` or its `<suite>` (only `browser`, `channel`, `headless`, `viewport.width`, `viewport.height`)
2. the config file
3. the default

Environment variables are not read for settings: they are for secrets only.

| Key | Default | |
|---|---|---|
| `app.<name>.url` | | The base URL of an app; needed only when a test opens one of its pages. |
| `browser` | `chromium` | `chromium`, `firefox` or `webkit` (Safari's engine). |
| `channel` | | `chrome` or `msedge`: the browser installed on the machine instead of Playwright's. |
| `headless` | `true` | |
| `timeout.ms` | `30000` | How long an action waits before failing. |
| `viewport.width`, `viewport.height` | `1280`, `720` | |
| `slow.mo.ms` | `0` | A pause before each action, to watch a run. |
| `evidence.dir` | `ui-evidence` | Relative to the directory the tests run in; add it to `.gitignore`. |
| `trace` | `off` | `on-failure` also keeps a Playwright trace of a failed test. |

The same suite in two browsers in one run:

```xml
<suite name="checkout">
  <test name="chromium"><parameter name="browser" value="chromium"/><classes>...</classes></test>
  <test name="firefox"><parameter name="browser" value="firefox"/><classes>...</classes></test>
</suite>
```

## Secrets

Users, passwords and keys live in environment variables, never in a `.properties` file. Each environment's file names the variable that holds each secret, and the test asks for the secret by that name, the same in every environment:

```properties
# ui-test-qa.properties
secret.admin.user=SHOP_QA_ADMIN_USER
secret.admin.password=SHOP_QA_ADMIN_PASSWORD
```

```properties
# ui-test-dev.properties
secret.admin.user=SHOP_DEV_ADMIN_USER
secret.admin.password=SHOP_DEV_ADMIN_PASSWORD
```

```java
open(LoginPage.class).loginAs(secret("admin.user"), secret("admin.password"));
```

A missing `secret.<name>` key or an unset variable fails the test, naming the key or the variable and never the value. Locally, export the variables (or set them in the IDE's run configuration); on CI, map them from the CI's secret store:

```yaml
env:
  SHOP_QA_ADMIN_USER: ${{ secrets.SHOP_QA_ADMIN_USER }}
  SHOP_QA_ADMIN_PASSWORD: ${{ secrets.SHOP_QA_ADMIN_PASSWORD }}
```

## Failure evidence

When a test fails, the library saves a screenshot of each tab, and the trace if `trace=on-failure`, to `<evidence.dir>/<test class>/<method>.png` (`-tab2.png`, `-trace.zip`). The paths go to the TestNG report. Open a trace with `npx playwright show-trace <file>` or at trace.playwright.dev.

## Browsers

Playwright downloads its browsers the first time it runs. On CI, install them and their system libraries beforehand, at the Playwright version you depend on:

```sh
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install --with-deps chromium"
```
