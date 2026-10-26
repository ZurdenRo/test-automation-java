# mobile-test-automation

A library for testing mobile apps through screens, on Appium (UiAutomator2) and TestNG. Android only for now; iOS is planned.

Your project writes the **screens** (one class per screen of the app, with what a user can do there) and the **tests** (TestNG classes that talk only to screens). The library opens a fresh Appium session for every test method on a free device, closes it afterwards, and keeps a screenshot, the UI tree and logcat when a test fails. It connects to an Appium server you run; it never starts servers or emulators.

The words used here are defined in [CONTEXT.md](CONTEXT.md), and the decisions behind them are in [docs/adr](docs/adr).

## Add it to a test project

The library is published to GitHub Packages as `com.company.qa:mobile-test-automation`. It needs Java 17 or later ([ADR-0005](docs/adr/0005-the-module-needs-java-17.md)). Appium's Java client, Selenium's `ExpectedConditions` and TestNG come with it, so do not declare them yourself ([ADR-0002](docs/adr/0002-appium-is-part-of-the-public-api.md)).

```groovy
repositories {
    mavenCentral()
    maven {
        url = "https://maven.pkg.github.com/ZurdenRo/test-automation-java"
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    testImplementation 'com.company.qa:mobile-test-automation:1.0.0'
    testRuntimeOnly 'ch.qos.logback:logback-classic:1.5.38'   // or any other SLF4J backend
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

test {
    useTestNG()
}
```

## Configure an environment

Each environment has its own file on the test classpath, `src/test/resources/mobile-test-<env>.properties`. The suite parameter `env` picks the file, and `qa` is the default. Keys that belong to one platform start with its name (`android.`).

```properties
# src/test/resources/mobile-test-qa.properties
appium.server.url=http://localhost:4723

# The app: an APK the Appium server can read (a path on the server's machine, or a URL)...
android.app=https://github.com/appium/android-apidemos/releases/download/v6.0.18/ApiDemos-debug.apk
# ...or a package that is already installed on the devices (set one of the two, not both)
#android.app.package=com.jeffprod.todo

# The serials `adb devices` lists, separated by commas; each runs one test at a time
android.devices=emulator-5554

# Secrets: the file names the environment variable, never the value
secret.admin.password=TODO_ADMIN_PASSWORD
```

| Key | Required | Default | What it is |
|---|---|---|---|
| `appium.server.url` | yes | | The Appium server's `http` or `https` URL |
| `android.app` | one of the two | | APK to install: a path on the server's machine, or a URL |
| `android.app.package` | one of the two | | Package already installed on the devices |
| `android.devices` | yes | | Device serials, separated by commas, without repeats |
| `wait.until.timeout.ms` | no | `10000` | How long `waitUntil` waits before it fails |
| `device.wait.timeout.ms` | no | `600000` | How long a test waits for a free device |
| `evidence.dir` | no | `mobile-evidence` | Where failure evidence goes, relative to the working directory |
| `secret.<name>` | no | | The environment variable that holds secret `<name>` |

Settings come only from this file. Environment variables hold secrets and nothing else ([root ADR-0001](../docs/adr/0001-environment-variables-hold-secrets-only.md)). A missing or wrong setting fails the test with a message that names the key and the file.

The app's data is cleared before every test (`noReset=false`), so each test starts from the app's first screen. The server installs an APK set with `android.app` if it is not on the device yet, and does not uninstall it between tests.

## Write screens

A screen extends `BaseScreen` and keeps the driver to itself. Use `AndroidScreen` when the screen needs something that only Android offers, such as `currentActivity()`, key events or UiAutomator selectors. A screen that extends only `BaseScreen` stays portable to iOS.

The implicit wait is 0, so wait explicitly with `waitUntil`. It takes Selenium's `ExpectedConditions` as they are, or a lambda for anything else, and it fails with a `TimeoutException` after `wait.until.timeout.ms`.

```java
// HomeScreen.java
import com.company.qa.mobile.BaseScreen;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;

import static org.openqa.selenium.support.ui.ExpectedConditions.elementToBeClickable;
import static org.openqa.selenium.support.ui.ExpectedConditions.visibilityOfElementLocated;

public class HomeScreen extends BaseScreen {

    private static final By VIEWS = AppiumBy.accessibilityId("Views");

    public HomeScreen(AppiumDriver driver) {
        super(driver);
    }

    public ViewsScreen openViews() {
        waitUntil(elementToBeClickable(VIEWS)).click();
        return new ViewsScreen(driver());
    }
}

// ViewsScreen.java
public class ViewsScreen extends BaseScreen {

    private static final By ANIMATION = AppiumBy.accessibilityId("Animation");

    public ViewsScreen(AppiumDriver driver) {
        super(driver);
    }

    public boolean showsAnimation() {
        return waitUntil(visibilityOfElementLocated(ANIMATION)).isDisplayed();
    }
}
```

The constructor must be public and take the driver (`AndroidDriver` for an `AndroidScreen`), so that a test can get the screen with `on(...)`.

A part of a screen that repeats or stands on its own, such as a list row or a dialog, is a component. It extends `BaseComponent`, gets the element of its root, and looks for everything inside that root. Its `waitUntil` receives the root rather than the driver, so the condition is a lambda:

```java
public class TaskRow extends BaseComponent {

    private static final By TITLE = AppiumBy.id("com.example:id/title");

    public TaskRow(AppiumDriver driver, WebElement root) {
        super(driver, root);
    }

    public String title() {
        return waitUntil(root -> root.findElement(TITLE)).getText();
    }
}
```

To find locators, look at the app in [Appium Inspector](https://github.com/appium/appium-inspector), or open the `.xml` file a failed test leaves behind.

## Write tests

A test extends `BaseMobileTest`. Each test method gets its own session, so it starts from the app's first screen. `on(Screen.class)` gives the screen that is showing now.

```java
import com.company.qa.mobile.BaseMobileTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

public class ViewsTest extends BaseMobileTest {

    @Test
    public void opensViewsFromHome() {
        assertTrue(on(HomeScreen.class).openViews().showsAnimation());
    }
}
```

`secret("admin.password")` returns the value of the environment variable that `secret.admin.password` names. Pass it to a screen, and never log it. `environment()` returns the suite's `env`, so a service client can read its own file for the same environment.

The suite picks the environment and the platform once, with two parameters. To run methods in parallel, list one device per thread. A thread that finds no free device waits for one.

```xml
<suite name="mobile" parallel="methods" thread-count="2">
    <parameter name="env" value="qa"/>
    <parameter name="platform" value="android"/>
    <test name="smoke">
        <classes>
            <class name="com.example.ViewsTest"/>
        </classes>
    </test>
</suite>
```

## Failure evidence

When a test fails, the library saves three files in `<evidence.dir>/<fully qualified test class>/` before it closes the session:

- `<method>.png`: the screenshot.
- `<method>.xml`: the UI tree.
- `<method>-logcat.txt`: the device log.

If a method fails more than once, the later files get the suffixes `-2`, `-3` and so on. The paths appear in the TestNG report and in the log. If one of the files cannot be taken, for example because the session was lost, the library logs a warning and skips it, and the test keeps its own failure. Add `mobile-evidence/` to your `.gitignore`.

## Run it locally

You need an Appium server with the UiAutomator2 driver, and an emulator or phone that `adb devices` lists. Start them however you like. Here are two ways.

**With the Android SDK on your machine:**

```sh
emulator -avd Pixel_6_API34 -no-window &   # any AVD; `emulator -list-avds` lists yours
npm install -g appium
appium driver install uiautomator2
appium                                      # listens on http://localhost:4723
```

**With Docker** (Linux with KVM). This is the image the module's own CI uses. The emulator and Appium run inside one container:

```sh
docker run -d --name android --device /dev/kvm -p 4723:4723 \
  -e EMULATOR_DEVICE="Samsung Galaxy S10" -e APPIUM=true \
  -v "$PWD/apk:/apk" \
  budtmo/docker-android:emulator_14.0_v3.8.0-p1
docker exec android adb devices             # emulator-5554 once it has booted
```

Inside the container, the Appium server reads the APK from its own file system. So `android.app` is the path in the container, such as `/apk/my-app.apk` from the volume above, or a URL.

**An app that is already installed.** Install it once, and point the config at its package instead of an APK:

```sh
adb -s emulator-5554 install ToDo_1.24.apk
```

```properties
android.app.package=com.jeffprod.todo
```

The library opens the app's launcher activity. It clears the app's data before every test, but it never uninstalls the app.

## Work on the library

```sh
./gradlew :mobile-test-automation:build              # unit tests, against a fake Appium server
./gradlew :mobile-test-automation:integrationTest    # against a real server and emulator-5554 (see above)
```

The integration tests run Appium's ApiDemos app. They expect a server at `http://localhost:4723` and a device named `emulator-5554`. In CI, the `mobile-integration` job starts both with the Docker image above, on every pull request to `main`.
