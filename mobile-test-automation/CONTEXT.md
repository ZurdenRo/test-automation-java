# Mobile Test Automation

A published library that lets test projects drive mobile applications through screens, built on Appium, which consumers use directly. Android first; iOS is planned and shapes the core, but is not built yet.

## Language

**Screen**:
A consumer project's own class, written by the consumer, that models one screen of the application under test and exposes what a user can do there as methods (e.g. `loginAs(user)`). Tests talk to it, never to the driver.
_Avoid_: Page, view, activity, page object

**Component**:
A consumer's class that models a part of a screen that repeats or stands on its own (a toolbar, a list row, a dialog), scoped to that part rather than to the whole screen. Screens contain components.
_Avoid_: Fragment, widget, view

**Driver**:
Appium's handle on the device in a session. Screens and components act on it; tests do not.
_Avoid_: Session (alone), client

**Mobile test**:
A consumer's test class that extends the library's test base class. It obtains its first screen from the library and from then on talks only to screens.
_Avoid_: Suite, spec, scenario

**Suite**:
The runner's grouping of test classes and the order they run in (in TestNG, `testng.xml`). The consumer's concern, not the library's: a mobile test never relies on another having run.
_Avoid_: Test class

**Session**:
What each test method gets, fresh: an Appium session on one device, with the app freshly installed and launched. Nothing a test leaves behind reaches another test.
_Avoid_: Context, run

**Appium server**:
The process the library connects to, at the URL in the environment's config file. It drives the devices. The library never starts it.
_Avoid_: Hub, grid, backend

**Device**:
One emulator or phone a session runs on, named by the serial `adb devices` shows (`emulator-5554`). A device serves one session at a time.
_Avoid_: Emulator (alone), phone, target

**Device pool**:
The devices listed for a platform. Each test thread takes a free one for its session and returns it afterwards; with one device, tests run one after another.
_Avoid_: Farm, fleet

**Platform**:
Android or, later, iOS. A suite names it once, with the `platform` parameter; it picks which keys of the config file are read and which platform package builds the session.
_Avoid_: OS, target

**App**:
The application under test, given as an APK (a path the Appium server can read, or a URL) or as a package already installed on the device. A black box: the library never needs its source.
_Avoid_: Build, binary, artifact

**Environment**:
One deployment of the app's backends (QA, staging), described by its own config file, `mobile-test-<env>.properties`. A suite names its environment once, with the `env` parameter, and runs against it; QA is the default.
_Avoid_: Stage, profile, target

**Secret**:
A user, password or key a test needs. It lives only in an environment variable; the environment's config file holds the variable's name, never the value, and a test asks for the secret by a name that is the same in every environment.
_Avoid_: Credential, sensitive setting

**Failure evidence**:
What the library keeps when a test fails, so it can be diagnosed without re-running: always a screenshot, the screen's UI hierarchy (Appium's page source) and the device log (logcat on Android) of that test.
_Avoid_: Artifacts, attachments, reports
