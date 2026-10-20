# The published API exposes no third-party types; its dependencies are Apache HttpClient and Jackson

This module is consumed by other teams, so its classpath is theirs. Apache HttpClient and Jackson are declared `implementation` (never `api`) and each is confined to one package-private class; no third-party type appears in any public signature. Both still reach the consumer's runtime classpath (Gradle publishes `implementation` as `runtime` scope), so they are the only third-party dependencies this module may carry. Config is `.properties`, which the JDK reads, so there is no YAML/JSON config parser either.

A dependency published by a library is resolved into every consumer, where Gradle raises conflicts to the highest version, which we never tested. Hiding Apache made ADR-0001 cheap: swapping 4.x for 5.x broke no one.

## Jackson is bundled on purpose

We first chose to ship no JSON library and make each consumer plug in a `BodyDeserializer`. We reversed that: consumers should not each write the same adapter, so `bodyAs(Type.class)` reads JSON out of the box. The cost is accepted: Jackson is on every consumer's classpath, and a consumer pinning an older Jackson will be raised to ours or ours to theirs. `bodyDeserializer(...)` remains as an optional override (another JSON library, special mapper settings).

## Consequences

- Unknown JSON fields are ignored by default, so an API gaining a field does not break unrelated tests.
- Advanced Apache features (interceptors, custom routing) are unavailable. If ever needed, add a narrow explicit extension point; do not expose Apache types.
- All code in `com.company.qa.http` so the Apache-backed implementation can be package-private; splitting into subpackages would force it public.
