# Context Map

Each module is its own context, with its own language and decisions. Decisions that span modules live in `docs/adr/` at the root.

- **HTTP test automation**: `http-test-automation/CONTEXT.md`, decisions in `http-test-automation/docs/adr/`. A library for talking to HTTP services from tests.
- **UI test automation**: `ui-test-automation/CONTEXT.md`, decisions in `ui-test-automation/docs/adr/`. A library for driving web applications from tests, built on Playwright.
- **Config**: `config-loader/CONTEXT.md`. Internal library that reads the YAML config for the three modules above (`docs/adr/0006`).
- **Mobile test automation**: `mobile-test-automation/CONTEXT.md`, decisions in `mobile-test-automation/docs/adr/`. A library for driving mobile applications from tests, built on Appium. Android first.

Settings come from YAML (`test-automation.yaml` and `test-automation-<env>.yaml`, `docs/adr/0005`), read by the config module; environment variables hold secrets only (`docs/adr/0001`).

The modules are published together, at one version, with `qa-bom` to pin them (`docs/adr/0007`). A consumer imports `platform('com.company.qa:qa-bom:<version>')` and lists the modules it uses without versions; the repository needs a token with `read:packages`.
