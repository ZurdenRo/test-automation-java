# The module connects to an Appium server and never starts one

A run's `mobile-test-<env>.properties` holds `appium.server.url`, and the module only connects to it. Whoever runs the tests provides the Appium server and the devices: the developer on a laptop (`emulator`, `adb`, `appium`), a container in CI, or, later, a cloud device farm. Moving between them changes a URL and a few capabilities, never code.

The module does not boot emulators or spawn Appium. That would tie it to one way of running, duplicate what CI and Docker already do, and make a farm a special case. It does not check that `android.app` exists either: the Appium server resolves it, so the path must exist where the server runs (a mounted volume, in Docker) or be a URL.

## Consequences

- Locally the developer starts the emulator and `appium` first; the README says how, and what errors a missing server gives.
- CI runs the emulator and Appium in one container (`budtmo/docker-android`, pinned) in a job of its own, apart from the build job.
- A device farm needs its own credentials and capabilities; both go through the config file and environment variables when it is built (secrets stay in variables, root ADR-0001).
- The module's integration tests need a server and a device; its unit tests do not.
