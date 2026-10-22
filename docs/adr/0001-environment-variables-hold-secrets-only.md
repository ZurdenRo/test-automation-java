# Environment variables hold secrets only; settings come from `.properties` files

In every module, a test run's settings (URLs, browser, timeouts) come from `.properties` resources checked in with the consumer's tests, and environment variables are read only for secrets: keys, passwords, users. A setting cannot be overridden by an environment variable, and a secret is never written in a `.properties` file.

Both modules first let an environment variable override any setting (`QA_HTTP_<RESOURCE>_BASE_URL`, `QA_UI_HEADLESS`), the usual twelve-factor shape. We dropped that: with two places for every setting, what a run used depended on the shell it ran in, and reading a checked-in file no longer told you. Switching environments is done by choosing a file instead: in UI tests, a suite names its environment's file with the `ui.config` parameter.

## Consequences

- What a run used is readable from the repository: the suite xml names the file, the file holds the values.
- One file per environment, so a value shared by all of them is repeated in each.
- CI chooses an environment by choosing a suite, not by exporting variables; it exports only secrets.
- In http-test-automation a setting comes from the builder or the service config; `HttpTransportBuilder.envPrefix(...)` and `.environment(...)` were removed before the first release.
