# The module needs Java 17

This module compiles for and runs on Java 17; http-test-automation and ui-test-automation stay on 11. A consumer of this module compiles its tests with 17 or later.

The Appium client sends every command through the JDK's `java.net.http.HttpClient`. On JDK 11 that client trips an assert of its own (`AssertionError` in `ResponseSubscribers$HttpResponseInputStream.read`) while reading a large response, such as a screenshot or a UI tree, whenever assertions are on. Gradle and Surefire turn assertions on for tests by default, so a consumer's run on 11 fails exactly when it needs the evidence. JDK 17 has the fix. On 11 the module could turn assertions off for `jdk.internal.net.http` from a static block, but that reaches into JDK internals from a library, and every consumer on 11 would depend on it staying in place.

## Consequences

- A consumer project on Java 11 cannot use this module; it moves to 17, which Gradle can provide through a toolchain without changing the JDK that runs Gradle.
- The modules no longer share one Java version. Moving http and ui to 17 is a separate decision.
- `EvidenceTest.aLargeUiTreeIsKeptWithAssertionsOn` keeps a large UI tree with assertions on, so a move back to 11 fails the build.
