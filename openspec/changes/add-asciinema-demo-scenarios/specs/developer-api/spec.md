## ADDED Requirements

### Requirement: Deterministic asciinema demo scenarios
The project SHALL provide deterministic local demo scenarios for asciinema recording that are separate from the manual interactive smoke demos.

#### Scenario: Recording scenarios are available
- **WHEN** a contributor wants to record polished terminal demos locally
- **THEN** the repository provides commands or scripts for the agent prompt composer, command palette and settings, and Unicode typed input scenarios

#### Scenario: Scenarios avoid build-tool noise
- **WHEN** a recording scenario is run through the documented recording command
- **THEN** the visible cast output contains the scenario content without Mill progress output, Scala CLI resolver output, or compiler progress output

#### Scenario: Scenario output is deterministic
- **WHEN** the same recording scenario is run twice with the same repository state and terminal dimensions
- **THEN** the visible scene order, captions, and feature steps are the same apart from timing metadata

### Requirement: Asciinema recording remains optional
The project SHALL keep asciinema recording as an optional local publishing workflow, not as a required build, test, or runtime dependency.

#### Scenario: Build does not require asciinema
- **WHEN** a contributor runs normal compile, test, formatting, lint, or OpenSpec validation commands
- **THEN** those commands do not require asciinema, expect, Node.js, npm, `agg`, `svg-term`, or browser tooling

#### Scenario: Recording writes local artifacts
- **WHEN** a contributor runs the documented asciinema recording commands
- **THEN** generated `.cast` files are written to a local artifact path and are not required inputs to compile, test, formatting, lint, or OpenSpec validation commands

### Requirement: Demo recording documentation
The project SHALL document how to record, replay, and publish the asciinema demo scenarios.

#### Scenario: Contributor plays a local recording
- **WHEN** a contributor has a generated `.cast` file
- **THEN** the documentation shows the exact `asciinema play` command needed to replay it locally

#### Scenario: Contributor publishes a README preview
- **WHEN** a contributor uploads a cast to asciinema.org for README use
- **THEN** the documentation shows the clickable SVG preview Markdown format `[![asciicast](https://asciinema.org/a/<id>.svg)](https://asciinema.org/a/<id>)`

#### Scenario: Documentation identifies scenario purpose
- **WHEN** a contributor reviews the recording documentation
- **THEN** each scenario states the feature story it demonstrates and the command that generates its local `.cast` file
