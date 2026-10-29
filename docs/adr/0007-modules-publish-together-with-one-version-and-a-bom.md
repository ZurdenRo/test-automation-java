# Modules publish together, with one version and a BOM

`config-loader`, `http-test-automation`, `ui-test-automation` and `mobile-test-automation` are published to the same Maven repository (GitHub Packages) under `com.company.qa`, all at the version in `gradle.properties`. Alongside them goes `qa-bom`, a `java-platform` that pins the four.

`config-loader` is published because the other three depend on it (ADR-0006) and that dependency is in their POM and Gradle metadata. It is still internal: it is an `implementation` dependency, so it reaches a consumer's runtime classpath but never the compile one, and a consumer does not declare it.

A consumer states the version once:

```groovy
dependencies {
    testImplementation platform('com.company.qa:qa-bom:1.0.0')
    testImplementation 'com.company.qa:ui-test-automation'
}
```

Reading from GitHub Packages needs a token with `read:packages`, even for the consumers in the company.

## Consequences

- One version for all modules: they share a repo and a release, so no combination of versions goes untested. Published versions are never overwritten, so a fix is a new version.
- Only CI publishes, when a tag `vX.Y.Z` is pushed and equals `version` (`.github/workflows/release.yml`). Locally, `publishToMavenLocal` or `includeBuild` serve to try a change.
- What the modules share in their build (toolchain, sources and Javadoc jars, test setup, publishing) lives in the convention plugins of `build-logic`, `qa.java-library` and `qa.publishing`. Mobile only overrides its toolchain to 17 (mobile ADR-0005), which its Gradle metadata publishes so a Java 11 consumer gets a clear error.
- The repository URL is `qaRepositoryUrl` in `gradle.properties`; moving to Nexus or Artifactory means changing it. Credentials are the environment variables `ORG_GRADLE_PROJECT_GitHubPackagesUsername` and `ORG_GRADLE_PROJECT_GitHubPackagesPassword` (ADR-0001), never in a file.
