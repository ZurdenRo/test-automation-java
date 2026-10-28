# UI Test Automation

A published library that lets test projects drive web applications through page objects, built on Playwright, which consumers use directly.

## Language

**Page object**:
A consumer project's own class, written by the consumer, that models one screen of the application under test and exposes what a user can do there as methods (e.g. `loginAs(user)`). Tests talk to it, never to the browser page.
_Avoid_: Page, screen, view

**Component**:
A consumer's class that models a part of a screen that repeats or stands on its own (a header, a modal, a table row), scoped to that part rather than to the whole screen. Page objects contain components.
_Avoid_: Fragment, widget, section, page component

**Browser page**:
Playwright's tab in which a test runs. Page objects and components act on it; tests do not.
_Avoid_: Page (alone), tab, window

**UI test**:
A consumer's test class that extends the library's test base class. It obtains its first page object from the library and from then on talks only to page objects.
_Avoid_: Suite, spec, scenario

**Suite**:
The runner's grouping of test classes and the order they run in (in TestNG, `testng.xml`). The consumer's concern, not the library's: a UI test never relies on another having run.
_Avoid_: Test class

**Browser context**:
The isolated browser session each test method gets, fresh: its own cookies, storage and browser pages. Nothing a test leaves behind reaches another test.
_Avoid_: Session, profile

**App**:
One web application under test, with a name and its own base URL. Every page object that can be opened names its app, so a test can move between apps without naming a host. There is no default app.
_Avoid_: Host, site, domain, base URL (alone)

**Environment**:
One deployment of the apps under test (QA, staging), described by its own config file, `test-automation-<env>.yaml`, over the shared `test-automation.yaml` (root `docs/adr/0005`). A suite names its environment once, with the `env` parameter, and runs against it; QA is the default.
_Avoid_: Stage, profile, target

**Secret**:
A user, password or key a test needs. It lives only in an environment variable; the environment's config file holds the variable's name, never the value, and a test asks for the secret by a name that is the same in every environment.
_Avoid_: Credential, sensitive setting

**Path**:
Where a page object lives within its app, relative to the app's base URL, possibly with named parameters (`/users/{id}`). It maps the app's screens for navigation; tests do not assert on it.
_Avoid_: URL, route, endpoint

**Opening a page**:
Navigating straight to a page object's path, in the current tab or a new one, and getting the page object back. How a test starts, or skips the steps it is not about.
_Avoid_: Visiting, going to

**Tab**:
One browser page within a test's browser context. A page object is bound to the tab it was created on and always acts there; there is no current tab to switch.
_Avoid_: Window, switching

**Failure evidence**:
What the library keeps when a test fails, so it can be diagnosed without re-running: always a screenshot, and a Playwright trace when configured.
_Avoid_: Artifacts, attachments, reports
