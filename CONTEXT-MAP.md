# Context Map

Each module is its own context, with its own language and decisions. Decisions that span modules live in `docs/adr/` at the root.

- **HTTP test automation**: `http-test-automation/CONTEXT.md`, decisions in `http-test-automation/docs/adr/`. A library for talking to HTTP services from tests.
- **UI test automation**: `ui-test-automation/CONTEXT.md`, decisions in `ui-test-automation/docs/adr/`. A library for driving web applications from tests, built on Playwright.

The contexts share configuration conventions, not code: settings come from `.properties` resources, and environment variables hold secrets only (`docs/adr/0001`).
