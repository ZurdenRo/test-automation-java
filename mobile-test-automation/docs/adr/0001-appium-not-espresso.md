# Built on Appium, not Espresso

The module drives mobile apps through the Appium Java client with the UiAutomator2 driver on Android (XCUITest on iOS, later), and consumers run their tests with TestNG.

Espresso was the first idea. It is an instrumentation framework: its tests are compiled into a second APK and run inside the app's process, under `AndroidJUnitRunner` (JUnit 4). That cannot be a JVM library with a TestNG base class like ui-test-automation (ADR-0002 there), and it has no iOS counterpart: XCUITest is Swift. It does not start emulators either; something else must provide the device, as with Appium.

## Considered options

- **Espresso (rejected)**: white-box and fast, but it needs the app's build, runs only under JUnit 4, and shares nothing with iOS.
- **Appium (chosen)**: black-box through a server, a JVM client, TestNG as it is, one library for both platforms. Slower than Espresso and one more process to run.

## Consequences

- The app is a black box: an APK, or a package already installed. We can only use what the APK exposes (accessibility ids, resource ids, text).
- A run needs an Appium server and a device, both outside this module (ADR-0003).
- Espresso tests inside an app's own repository remain possible, as a different project.
