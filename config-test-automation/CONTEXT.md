# Config Test Automation

An internal library that reads a test run's settings from YAML for http, ui and mobile, so that the three behave the same. Consumers never import it.

## Language

**Config**:
The settings of one environment: `test-automation-<env>.yaml` laid over `test-automation.yaml`, both on the classpath.
_Avoid_: Properties, settings file

**Environment**:
One deployment of the systems under test (`qa`, `staging`), named by the suite's `env` parameter; `qa` by default. Its file is required.
_Avoid_: Profile, stage

**Shared file**:
`test-automation.yaml`, optional: what every environment has in common.
_Avoid_: Base, application.yaml

**Section**:
The settings under one key: `ui`, `mobile`, `services.<name>`. Each module reads its own and ignores the rest.
_Avoid_: Block, namespace

**Setting**:
One value with its full key and the file it came from, so an error can name both.
_Avoid_: Property, entry

**Secret**:
A value that lives only in an environment variable; a section's `secrets:` names the variable, never the value.
_Avoid_: Credential
