# A `platform` parameter picks which mobile keys are read

Mobile tests have a second axis besides the environment: the platform. A suite names it once, with `<parameter name="platform" value="ios"/>`, next to `env`; without it the platform is `android`. The environment's file, `test-automation-<env>.yaml`, holds both platforms' keys in their own section of `mobile` (`mobile.android.app`, `mobile.ios.app`), and only the chosen platform's are read. Keys that do not depend on the platform (`mobile.appium-server-url`, `mobile.wait-until-timeout-ms`) are not nested.

We could have set the platform inside the file, but running iOS against QA would then need a second file for the same environment, against the rule of one file per environment (ADR-0002). It adds one suite parameter to the one ADR-0002 introduced; no other source of settings (ADR-0001) is added.

## Consequences

- Moving a suite to iOS is a one-line change, and it cannot half-switch a test: the environment and the platform are named once each.
- A key of the other platform in the file is ignored, not an error; a missing key of the chosen platform names the key expected.
- Only mobile-test-automation reads `platform`; http and ui ignore it.
- iOS adds its own package and keys later; nothing here designs them.
