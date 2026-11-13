# One `env` parameter picks every module's file

> Partly superseded by [0005](0005-config-comes-from-yaml-a-shared-file-and-one-per-environment.md): the files are `test-automation-<env>.yaml`, one set for all modules, and http reads `services.<name>`. The `env` parameter still stands.


A suite names its environment once, with `<parameter name="env" value="staging"/>`, and every config file it reads is named after it: `<name>-<env>.properties`. UI tests read `ui-test-<env>.properties` and expose the environment with `environment()`, so a service client used by the same test names its file the same way: `new UsersClient(environment())` reads `users-service-<env>.properties`. Without the parameter the environment is `qa`, which is also what running a single test from the IDE uses.

We first had UI tests name their file directly (`<parameter name="ui.config" value="ui-test-staging.properties"/>`). With one such parameter per module or service, a test that used the UI and an API could run them against different environments when one parameter was changed and another forgotten.

## Consequences

- Moving a suite to another environment is a one-line change, and it cannot leave half the test behind.
- File names are a convention: a file named otherwise is not found, and the error names the file expected for the environment.
- An environment is a file per module; adding one means adding each module's file, even if only a value differs.
- http-test-automation does not know TestNG, so it does not read `env` itself: the test passes the environment to the client. A UI or mobile test has `environment()`, available from a `@BeforeClass` method on, so a client is created once per class: `new UsersClient(environment())`. A test of an API only, with no base class to ask, takes the parameter with TestNG's own `@Parameters("env")` and `@Optional("qa")`.

## Considered options

- **http reads `env` from the running TestNG test (rejected)**: tried in 1.2.0, with a `HttpServiceClient(service)` constructor reading `Reporter.getCurrentTestResult()`. It saved the test from passing the environment, but made http depend on TestNG, which no other part of its API needs, and a client created in a field initializer or a constructor failed. Withdrawn in 1.3.0.
- **A `BaseHttpTest` (rejected)**: a test that uses UI and API cannot extend it and `BaseUiTest`, so it would only serve suites of an API alone, which already have `@Parameters`.
- **A default environment in `HttpServiceClient(service)` (rejected)**: a client could run on `qa` while its suite runs on staging, without anyone noticing.
