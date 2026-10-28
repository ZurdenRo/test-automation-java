# Config comes from YAML: a shared file and one per environment

A consumer's settings live in `test-automation-<env>.yaml` (required) laid over `test-automation.yaml` (optional, what every environment shares), both on the test classpath. One set of files serves every module the consumer uses, each reading its own top-level section: `ui`, `mobile`, and `services` with one entry per HTTP service. The `env` parameter of ADR-0002 still names the environment, and `qa` is still the default. This replaces the `.properties` files of ADR-0001 and the file naming of ADR-0002; environment variables still hold secrets only.

```yaml
# test-automation.yaml                    # test-automation-qa.yaml
ui:                                       ui:
  browser: chromium                         apps:
  timeout-ms: 30000                           shop:
services:                                       url: https://shop.qa.example.com
  users:                                        secrets:
    headers: { Accept: application/json }         user: SHOP_QA_USER
                                          services:
                                            users:
                                              base-url: https://users.qa.example.com
```

- **Merge**: key by key, the environment's file winning. A list is replaced whole, and a blank or `null` value is absent, so it never hides a shared value.
- **Files**: the environment's file is required, so `env=stagin` fails naming `test-automation-stagin.yaml` instead of running on the shared file alone. A project with one environment needs one file.
- **Keys**: nested YAML in kebab-case (`base-url`, `viewport.width`, `android.devices`), one way to write each, never a key with a dot. Platform keys of mobile nest under `mobile.android` (replacing the prefix of ADR-0004).
- **Strict**: an unknown key, an unknown top-level section, a duplicate key, or a map or list where a value goes fails, naming the key and the file. Reading values of the consumer's own from these files is out of scope.
- **Text, YAML 1.2**: values are read as text and validated as before, so `timeout-ms: "30000"` and `30000` are the same and `trace: off` is the text `off`. snakeyaml-engine, not SnakeYAML, whose YAML 1.1 turns `off`, `no` and `yes` into booleans.
- **No interpolation**: `${VAR}` is not supported. A secret is named under the `secrets:` of the thing that owns it (the variable's name, never its value): a service (`services.<name>.secrets`) or a web app (`ui.apps.<app>.secrets`), and under `mobile` for the mobile module. Two services, or two apps, can each have an `api-token` or a `password`.
- **Sources**: a value remembers the file it came from (or the `<parameter>` that overrode it) and errors say so; the first log line of a run lists the files read.

## Consequences

- A value shared by every environment is written once; adding an environment is one file for all modules.
- `HttpServiceClient` takes the service and the environment: `super("users", env)`. http-test-automation still does not know TestNG, so the test passes `environment()`; injecting clients, which ADR-0002 left for later, stays open.
- Every module's loader migrates; no `.properties` is read after that. Nothing was published, so there is no compatibility to keep.
- The shared parsing lives in one module (ADR-0006).
