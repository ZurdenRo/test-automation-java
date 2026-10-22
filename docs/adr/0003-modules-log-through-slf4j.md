# Modules log through SLF4J; the consumer picks the backend

Each module logs through the SLF4J API, as an `implementation` dependency, and ships no backend: the consumer's backend (Logback, Log4j) decides levels and where lines go. `WARN` is for configuration a run accepts but likely did not mean, `INFO` for what a run used (environment, file, browser) and what it kept (evidence), `DEBUG` for per-test detail. Secret values are never logged.

A library that ships a backend fights the consumer's own; `java.lang.System.Logger` needs no dependency but lands in java.util.logging unless the consumer bridges it. TestNG, which UI tests already depend on, uses SLF4J, so a consumer has it on the classpath anyway.

## Consequences

- A consumer with no backend sees one SLF4J warning and no logs; the README says what to add.
- The module's own tests use Logback, test-scoped, and assert on log lines with a `ListAppender`.
- Anything that may hold a secret is logged by name only: secrets by their variable, headers by name, query parameters with their values elided.
