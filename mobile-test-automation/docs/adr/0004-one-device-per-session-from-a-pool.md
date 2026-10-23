# One session per test method, on a device taken from a pool

`BaseMobileTest` opens an Appium session before each test method, on a device no other test is using, with the app's data cleared (`noReset` off, so Appium resets it; `fullReset`, which uninstalls and reinstalls, is slower and not used), and closes it afterwards, keeping failure evidence if the test failed. A device runs one session at a time, so parallel runs need several devices: `<platform>.devices` lists their serials (`android.devices=emulator-5554,emulator-5556`), each test thread takes a free one and returns it, and waits if none is free, up to `device.wait.timeout.ms` (ten minutes by default: it waits for other tests to finish). With one device, tests run one after another and the consumer changes nothing.

The same lifecycle as ui-test-automation's browser context (its ADR-0002): isolation first, speed second. A data reset and a new session cost seconds per test; sharing a session per class is possible later if a consumer asks for it and accepts the leaks between its tests.

## Consequences

- TestNG `parallel="methods"` works without consumer changes, up to the number of devices.
- More threads than devices means waiting, not failing; past `device.wait.timeout.ms` the error says which pool ran dry.
- The lifecycle lives in an internal class that knows no runner; `BaseMobileTest` is a thin adapter, as `BaseUiTest` is.
- `<platform>.app` and `<platform>.devices` are different things: what is tested and where it runs. The module installs the app on whichever device a session takes.
