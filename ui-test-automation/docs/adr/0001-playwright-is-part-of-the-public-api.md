# Playwright is part of the public API

Unlike http-test-automation, which hides Apache HttpClient and Jackson behind its own types (its ADR-0002), this module exposes Playwright: it is an `api` dependency, and Playwright types (`Page`, `Locator`) appear in the protected surface of the page object and component base classes. Consumers write their page objects with Playwright directly. The module adds structure (base classes, lifecycle, configuration), not an abstraction over Playwright.

## Considered options

- **Wrap Playwright (rejected)**: it would keep consumers off Playwright's types, as http does with Apache. But Playwright's surface (locators by role, auto-waiting, web-first assertions) is large and is the reason to use it; wrapping it means re-implementing it, always behind, and consumers asked for direct access.
- **Wrappers with an escape hatch (rejected)**: two ways to do everything, and the escape hatch becomes the main way.

## Consequences

- Upgrading Playwright can break consumers' page objects; a Playwright upgrade is a breaking change of this module unless Playwright's release says otherwise.
- The consumer's Playwright version is the one we publish; a consumer that pins another is raised to ours or ours to theirs.
- Tests still don't touch the browser page: page objects keep it protected (see the page object and component base classes).
