# http reads `env` from the running TestNG test

Supersedes the last consequence of [0002](0002-one-env-parameter-names-every-file.md), which said http does not know TestNG and left to the test passing `environment()` to each client.

Every consumer of the framework runs its tests with TestNG: that is a rule, not an assumption. So `HttpServiceClient` gets a constructor that takes only the service, `super("users")`, and reads the environment of the TestNG `<test>` running on the current thread (`Reporter.getCurrentTestResult()`, its `<test>`'s parameters with the suite's), by the same rule as ui and mobile: `<parameter name="env">`, or `qa` without one. A test no longer has to pass `environment()`, and an API-only suite, with no `BaseUiTest` to ask, gets the suite's environment too.

The rule itself, the parameter's name, the default and how errors say why the environment was picked, moved to `config-loader` (`Config.environmentFrom`, `Config.environmentCauseMsg`): ui and mobile had a copy each, and a third would drift.

## Considered options

- **TestNG as `compileOnly`, isolated so that the client still works without it (rejected)**: it protects consumers that the rule says do not exist.
- **The environment in a system property or a holder in `config-loader` that ui and mobile fill (rejected)**: no TestNG in http, but it misses API-only suites, and must be kept per thread and per `<test>` for parallel runs, which TestNG already does.
- **Default to `qa` when no test is running (rejected)**: a client could then run on another environment than its suite without anyone noticing, the very thing 0002 prevents.

## Consequences

- http depends on TestNG as `implementation`; no TestNG type is part of its API (http ADR-0002).
- The client must be created while TestNG runs a test or configuration method: in `@BeforeClass`, `@BeforeMethod` or a test. TestNG builds the test class before running any of them, so a client created in a field initializer or a constructor fails, saying where to create it. Threads a test starts see its environment.
- In `@BeforeSuite`, with several `<test>`s naming different environments, the first `<test>`'s is read.
- `HttpServiceClient(service, environment)` stays, for a client that must run on another environment than its test.
