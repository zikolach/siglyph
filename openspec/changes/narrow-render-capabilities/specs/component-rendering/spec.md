## ADDED Requirements

### Requirement: Internal renderer capabilities match supported screen modes

The runtime SHALL expose append publishing and resize recovery preparation only through dedicated internal capabilities supported by the selected renderer. This refactoring SHALL preserve public APIs and terminal output byte streams.

#### Scenario: Normal screen and width-only alternate screen support transcript operations
- **WHEN** a normal-screen policy is selected in normal or width-only alternate mode
- **THEN** it implements append publishing and resize recovery preparation capabilities
- **AND** existing transcript output and resize behavior remain unchanged

#### Scenario: Fullscreen viewport does not advertise transcript operations
- **WHEN** a fullscreen viewport policy is selected
- **THEN** it implements neither append publishing nor resize recovery preparation capabilities
- **AND** the common renderer contract contains no unsupported transcript methods or throwing fullscreen implementations

#### Scenario: Resize recovery data is renderer-neutral
- **WHEN** runtime scheduling supplies prepared resize recovery to the renderer
- **THEN** the internal prepared data type is independent of the TUI companion
- **AND** generation checks and scheduling remain runtime-owned

### Requirement: Component rendering services remain read-only

Component consumers SHALL obtain rendering observations through a read-only RenderServices interface rather than depending on mutable TUI internals, without removing public APIs.

#### Scenario: Image consumer reads rendering services
- **WHEN** an image component needs runtime rendering observations
- **THEN** it uses the read-only rendering services interface
- **AND** scheduling and runtime mutation remain owned by TUI
