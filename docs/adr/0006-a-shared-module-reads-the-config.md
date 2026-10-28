# A shared module reads the config

`config-test-automation` reads, merges and checks the YAML of ADR-0005, and http, ui and mobile depend on it as an `implementation` dependency: none of its types appears in their public APIs, and a consumer neither imports nor declares it. It has the same Java version as http and ui (11).

`CONTEXT-MAP.md` used to say the modules share conventions, not code. That held while each module read its own file, at the cost of three copies of the same loader. With one set of files read by every module, the merge, the strictness and the sources must behave the same in all three, and three copies would drift.

## Consequences

- The modules still share nothing else, and do not depend on each other.
- Each module keeps what is its own: which keys exist, their defaults and its exception type (`UiConfigException`), made from the module's `ConfigException`.
- A fix to how config is read ships in every module, which is now one dependency version.
- Exposing the module, for a consumer's own values, would be a separate decision.
