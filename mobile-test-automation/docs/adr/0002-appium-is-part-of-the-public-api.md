# Appium is part of the public API

Like ui-test-automation with Playwright (its ADR-0001), and unlike http-test-automation with Apache HttpClient, this module exposes its engine: the Appium Java client is an `api` dependency, and `AppiumDriver`, `By` and `WebDriverWait` appear in the protected surface of the screen and component base classes. Consumers write their screens with Appium directly. The module adds structure (base classes, lifecycle, configuration), not an abstraction over Appium.

`BaseScreen` and `BaseComponent` expose `AppiumDriver`, common to both platforms. What only one platform offers (`startActivity`, `pressKey`, UiAutomator selectors) comes through that platform's package: `AndroidScreen extends BaseScreen` exposes `AndroidDriver`. A screen that does not need it stays portable to iOS; one that does says so in its `extends`.

Waits are explicit. The implicit wait stays at 0, and `BaseScreen` and `BaseComponent` offer a protected `waitUntil(...)` over `WebDriverWait`, with the default timeout from `wait.timeout` in the config file. The implicit wait applies to every lookup, so checking that something is *not* there costs a full timeout, and mixing it with explicit waits gives unpredictable total waits.

## Consequences

- Upgrading Appium or Selenium can break consumers' screens; an upgrade is a breaking change of this module unless their release says otherwise.
- TestNG, Appium's Java client and the Selenium types it brings are `api` dependencies; a consumer pinning other versions is raised to ours or ours to theirs.
- Every protected member of the base classes is API we cannot change without breaking classes owned by other teams; keep them small.
- Tests still don't touch the driver: screens keep it protected.
