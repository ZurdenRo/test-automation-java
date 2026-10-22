# UI tests extend a TestNG base class over a runner-neutral core

Consumers run their tests with TestNG. A consumer's UI test extends the library's `BaseUiTest`, which opens a fresh browser context before each test method, keeps failure evidence when it fails, and closes the context after. It gives the test page objects through `open(Type.class)` (navigate to its path), `to(Type.class).with(name, value).open()` (a path with parameters), `openInNewTab(Type.class)` and `on(Type.class)` (wrap the tab as it is, without navigating), and does not expose the browser page, so tests talk only to page objects. Page objects extend `BasePage` and components extend `BaseComponent`; these two hold the browser page (or a component's root locator) as protected.

`BaseUiTest` is a thin adapter: the lifecycle (launching the browser, creating contexts, screenshots, traces) lives in an internal class that knows no runner.

## Considered options

- **TestNG listener (rejected)**: TestNG cannot inject arbitrary objects into `@Test` methods, so a test would fetch its browser page from a static accessor. It leaves the test class free to extend something else, but exposes static state and the browser page itself.
- **JUnit 5 extension (deferred)**: the cleanest form (the page object is a test method parameter), but consumers use TestNG. The runner-neutral core makes it an additive change when one asks for it.

## Consequences

- A consumer's UI test can extend no other class.
- TestNG is an `api` dependency, like Playwright: its types are in `BaseUiTest`'s signatures, and a consumer pinning another TestNG version is raised to ours or ours to theirs.
- Every protected member of `BaseUiTest`, `BasePage` and `BaseComponent` is API we cannot change without breaking classes owned by other teams; keep them small (http's ADR-0003 learned the same).
- Built for parallel runs from the start: each thread has its own browser and each test method its own browser context, so TestNG `parallel="methods"` needs no consumer changes.
- The module's own tests use JUnit and run TestNG programmatically to test `BaseUiTest`.
