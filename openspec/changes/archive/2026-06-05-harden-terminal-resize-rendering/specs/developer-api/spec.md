## ADDED Requirements

### Requirement: Robust interactive runtime expectation
The public interactive TUI runtime SHALL be suitable as a solid application building block under resize and narrow terminal conditions without adding runtime dependencies.

#### Scenario: Application survives narrow resize
- **WHEN** an application using the public `TUI.run()` API is resized to a narrow positive terminal width
- **THEN** the runtime preserves terminal state and continues handling input instead of crashing due to over-wide rendered lines

#### Scenario: No dependency added for resize hardening
- **WHEN** resize hardening is implemented for JVM and Scala Native backends
- **THEN** the Mill build does not add third-party runtime dependencies

### Requirement: Resize hardening documentation
The project documentation SHALL describe resize/narrow-width behavior, diagnostics, and the continued component width contract.

#### Scenario: Documentation explains runtime safety net
- **WHEN** a contributor reads runtime or component documentation
- **THEN** it explains that components must render within width and that the TUI runtime sanitizes final output to protect terminal sessions

#### Scenario: Smoke docs include resize coverage
- **WHEN** a developer performs interactive smoke testing
- **THEN** docs include resize and narrow-width checks for JVM and Scala Native demos
