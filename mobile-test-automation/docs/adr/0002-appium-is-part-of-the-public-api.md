# Appium is part of the public API

Like ui-test-automation with Playwright (its ADR-0001), and unlike http-test-automation with Apache HttpClient, this module exposes its engine: the Appium Java client is an `api` dependency, and `AppiumDriver`, `By` and `WebElement` appear in the protected surface of the screen and component base classes. Consumers write their screens with Appium directly. The module adds structure (base classes, lifecycle, configuration), not an abstraction over Appium.

`BaseScreen` and `BaseComponent` expose `AppiumDriver`, common to both platforms. What only one platform offers (`startActivity`, `pressKey`, UiAutomator selectors) comes through that platform's package: `AndroidScreen extends BaseScreen` exposes `AndroidDriver`. A screen that does not need it stays portable to iOS; one that does says so in its `extends`.

Waits are explicit. The implicit wait stays at 0, and `BaseScreen` and `BaseComponent` offer a protected `waitUntil(...)` over the Appium client's `FluentWait`, with the default timeout from `wait.until.timeout.ms` in the config file. The implicit wait applies to every lookup, so checking that something is *not* there costs a full timeout, and mixing it with explicit waits gives unpredictable total waits.

Since version 11 the Appium client brings only `selenium-api`, without Selenium's `ExpectedConditions`. We add `selenium-support` as an `api` dependency so that a screen passes them to `waitUntil` as they are: `waitUntil(elementToBeClickable(SAVE)).click()` says what it waits for in one name, where a lambda repeats the lookup, the checks and the null in every screen, and anyone coming from Selenium on the web already knows them. Only part of them makes sense in a native app (visibility, clickability, text, attributes, counts, and `and`/`or`/`not`); those about titles, URLs, frames, alerts and JavaScript are for browsers. A component's `waitUntil` gets its root element, not the driver, so it keeps taking lambdas. The version of `selenium-support` must be the `selenium-api` version the Appium client brings, or the two Selenium jars disagree.

## Consequences

- Upgrading Appium or Selenium can break consumers' screens; an upgrade is a breaking change of this module unless their release says otherwise.
- TestNG, Appium's Java client, the Selenium types it brings and `selenium-support` are `api` dependencies; a consumer pinning other versions is raised to ours or ours to theirs.
- Upgrading the Appium client means upgrading `selenium-support` to its `selenium-api` version, in `gradle/libs.versions.toml`.
- Every protected member of the base classes is API we cannot change without breaking classes owned by other teams; keep them small.
- Tests still don't touch the driver: screens keep it protected.
