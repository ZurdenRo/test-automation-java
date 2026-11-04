# config-loader

Read a test run's settings from YAML, so that `http-test-automation`, `ui-test-automation` and `mobile-test-automation` find, merge and check them the same way.

This module is internal. It reaches a project's runtime classpath through those three, and a project neither declares nor imports it (root ADR-0006). This README is for whoever works on those modules. To write a project's config, see the Configuration section of the module's own README, which lists its keys.

## The files

Two files on the classpath, usually in `src/test/resources`:

| File | Required | Holds |
| --- | --- | --- |
| `test-automation.yaml` | no | What every environment shares |
| `test-automation-<env>.yaml` | yes | One environment's settings |

An environment's name is made of letters, digits, `-` and `_`, since it is part of the file name. `Config.DEFAULT_ENV` is `qa`.

The top level holds only the sections `ui`, `services` and `mobile`. Each module reads its own and ignores the rest, so a project that uses several modules has one set of files:

```yaml
# test-automation.yaml
ui:
  browser: chromium
  viewport:
    width: 1280
    height: 720
mobile:
  android:
    devices: [emulator-5554, emulator-5556]
```

```yaml
# test-automation-staging.yaml
ui:
  browser: firefox
  viewport:
    width: 1920
mobile:
  android:
    devices: [emulator-5560]
services:
  users:
    base-url: https://users.staging.example.com
```

## How the files merge

The environment's file is laid over the shared one, key by key. For `staging` above:

| Key | Value | Why |
| --- | --- | --- |
| `ui.browser` | `firefox` | The environment's file wins |
| `ui.viewport.width` | `1920` | The environment's file wins |
| `ui.viewport.height` | `720` | Only the shared file sets it |
| `mobile.android.devices` | `[emulator-5560]` | A list is replaced whole, not appended to |
| `services.users.base-url` | `https://users.staging.example.com` | Only the environment's file sets it |

A blank value, `null` and `~` all count as absent. So a blank in the environment's file never hides a value of the shared file, and an environment cannot unset a shared key.

## The rules of a file

- **Values are text.** `timeout-ms: 30000` and `timeout-ms: "30000"` are the same, and `trace: off` is the text `off`, not a boolean. Whoever reads a value decides its type.
- **Keys are nested and in kebab-case.** A key holds no dot, because a dot separates the levels of a path: write `viewport:` and `width:` on two levels, not `viewport.width:`.
- **A value is a scalar or a list of scalars.** A list of lists or of maps is an error.
- **A key is either a value or a section.** `ui.viewport` cannot be a value in one file and a section in the other.
- **Duplicate keys and unknown sections are errors.** A typo such as `servics:` fails.
- **There is no interpolation.** `${...}` is plain text.
- **A secret never goes in a file.** `secrets.<name>` holds the name of the environment variable, not its value.

## Reading from a module

`Config.load` reads, merges and checks the files. A `Section` reads the settings under one key, and a `Setting` turns one value into its type:

```java
Config config = Config.load("staging", "for <parameter> env 'staging'");
Section ui = config.section("ui");

ui.rejectUnknown(List.of("browser", "timeout-ms", "viewport.width"), List.of("apps"));

String browser = ui.find("browser").map(Setting::value).orElse("chromium");
int timeoutMs = ui.find("timeout-ms").map(Setting::positiveInt).orElse(30_000);
String baseUrl = config.section("services").section("users").require("base-url", "").value();
Set<String> apps = ui.section("apps").names();
```

The second argument of `load` explains where the environment's name came from, for the error when its file is missing. It is null when that needs no explaining.

| Method | Use |
| --- | --- |
| `Section.find(key)` | A value, or empty when absent |
| `Section.require(key, hint)` | A value that must be there; `hint` is added to the error |
| `Section.list(key)` | A list, or empty when absent |
| `Section.names()` | The first-level names, such as the services of `services` |
| `Section.isEmpty()` | Whether the section holds no setting |
| `Section.rejectUnknown(keys, prefixes)` | Fails on a key the module does not read; any name is allowed under `prefixes` |
| `Section.secret(name, env)` | The secret `secrets.<name>` points at, read from `env` |
| `Setting.positiveInt()`, `nonNegativeInt()`, `bool()`, `oneOf(allowed)` | The value as that type, or an error naming key, value and file |
| `Setting.invalid(expected)` | The same error, for a check of the module's own |

`Setting.of(key, value, source)` wraps a value that came from elsewhere, so a TestNG `<parameter>` that overrides a key gets the same checks and errors. For example, `source` can be `<parameter> browser`.

## Secrets

The file names the variable, and the same secret name works in every environment:

```yaml
# test-automation-qa.yaml
services:
  payments:
    secrets:
      api-token: PAYMENTS_QA_TOKEN
```

```java
String token = config.section("services.payments").secret("api-token", System.getenv());
```

`env` is a parameter so that tests can pass their own map. The modules read secrets from nowhere else. A missing or blank variable is an error that names the variable and the file that points at it, and the value never appears in a message (root ADR-0001).

## Errors

Everything that fails throws `ConfigException`, unchecked, with a message that names the key and the file:

```text
'ui.timeout-ms' in test-automation-qa.yaml is 'abc': expected a positive whole number
Unknown key 'ui.heedless' in test-automation-qa.yaml: expected one of browser, timeout-ms, viewport.width, or a name under apps
Config file 'test-automation-staging.yaml' (for <parameter> env 'staging') not found on the classpath
```

No type of this module may appear in the public API of the modules that use it. Each one catches `ConfigException` and rethrows its own exception, such as `UiConfigException` (root ADR-0006).

## Logging

The module logs through SLF4J, and only at `DEBUG`: the environment and the files read. `Config.files()` gives those files to a module that wants to report them, as ui and mobile do at `INFO`.

## Changing this module

- **A new key** belongs to the module that reads it: add it to that module's loader and README. Nothing changes here.
- **A new top-level section** goes in `Config.SECTIONS`, along with the "expected" lists in the error messages of `Config.parse`.
- **A change to how files are read** reaches every module and is released with them at one version (root ADR-0007).

The tests in `ConfigTest` build a `Config` from text with `Config.of`, and `LoadTest` reads real files from the classpath:

```bash
./gradlew :config-loader:test
```

The decisions behind this module are in `docs/adr/` at the repo root: ADR-0005 for the YAML format and the merge, ADR-0006 for the shared module, and ADR-0001 for secrets. Its terms are defined in `CONTEXT.md`.
