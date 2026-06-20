## ADDED Requirements

### Requirement: Optional terminal integration APIs remain source-compatible
Public APIs for terminal title and progress SHALL preserve existing `Terminal` implementer source compatibility by using optional capabilities or helper methods instead of required abstract methods.

#### Scenario: Existing terminal implementation compiles
- **WHEN** a project-defined terminal backend implements the existing `Terminal` methods only
- **THEN** it continues to compile after terminal title and progress APIs are added

#### Scenario: Capability support is discoverable
- **WHEN** application code uses the public terminal integration API
- **THEN** it can determine whether title or progress support was applied without inspecting backend internals

### Requirement: Terminal integration APIs are documented
Public terminal integration APIs SHALL include Scaladoc and project documentation covering support detection, unsupported behavior, timeout behavior, and protocol scope.

#### Scenario: Public API Scaladoc explains unsupported behavior
- **WHEN** a developer reads Scaladoc for title, progress, background color, or color-scheme APIs
- **THEN** the docs explain whether unsupported terminals return an empty or false result instead of throwing for normal lack of support

#### Scenario: Runtime docs explain query ownership
- **WHEN** a developer reads runtime documentation
- **THEN** the docs state that `TUI` owns terminal color query request/response correlation and terminal backends remain write/input providers
