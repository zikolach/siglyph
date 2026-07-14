## MODIFIED Requirements

### Requirement: Unicode table generation
The library SHALL use a Scala CLI script to generate committed Unicode display-width data, Unicode 17.0.0 grapheme-segmentation runtime properties, and official Unicode 17.0.0 GraphemeBreakTest-derived test vectors from immutable versioned source URLs.

#### Scenario: Generated Unicode version is recorded
- **WHEN** Unicode tables or grapheme test vectors are generated
- **THEN** each generated output records Unicode version 17.0.0 and the exact immutable versioned source URLs used

#### Scenario: Unicode tables are reproducible
- **WHEN** the Unicode generation script is run repeatedly with the same Unicode 17.0.0 inputs and generator revision
- **THEN** it reproduces every committed runtime and test-data file byte-for-byte

#### Scenario: Generated properties cover segmentation rules
- **WHEN** Unicode 17.0.0 grapheme runtime data is generated
- **THEN** it includes the Grapheme_Cluster_Break, Extended_Pictographic, and Indic_Conjunct_Break properties required for UAX #29 default extended grapheme clusters

#### Scenario: Official fixtures are version matched
- **WHEN** GraphemeBreakTest-derived vectors are generated
- **THEN** the generator uses the immutable Unicode 17.0.0 GraphemeBreakTest source and fails if its version does not match the runtime property inputs

### Requirement: Public hardware cursor positioning option
The public core API SHALL expose the backend-independent `TUIOptions.hardwareCursorPositioning` option for enabling or disabling hardware cursor positioning from structured `ComponentRender.cursorPlacements`, without requiring application code to emit raw terminal escape sequences or cursor sentinel strings.

#### Scenario: Default preserves existing cursor behavior
- **WHEN** an application constructs a TUI without configuring hardware cursor positioning
- **THEN** fake cursor rendering remains visible and no structured-cursor-derived hardware cursor movement is performed

#### Scenario: Application opts in through public API
- **WHEN** an application enables hardware cursor positioning through the public TUI options
- **THEN** the shared TUI runtime uses the selected surviving structured `CursorPlacement` to place the terminal hardware cursor where supported by the backend abstraction

#### Scenario: API compiles on JVM and Native core
- **WHEN** the hardware cursor positioning option is compiled for JVM core and Scala Native core modules
- **THEN** it does not depend on JVM-only, Native-only, Node.js, or third-party runtime APIs

### Requirement: Hardware cursor positioning documentation
The project documentation SHALL describe `TUIOptions.hardwareCursorPositioning`, its opt-in default, fake-cursor preservation, structured cursor metadata source, ordinary-string behavior, and testing expectations.

#### Scenario: Documentation lists behavior and caveats
- **WHEN** a developer reads TUI runtime or editor documentation
- **THEN** it explains that hardware cursor positioning is opt-in, uses the selected structured `CursorPlacement` rather than marker scanning, preserves fake cursor rendering, and treats ordinary former cursor bytes as inert text with no cursor authority

#### Scenario: Smoke docs include IME/cursor checks
- **WHEN** a developer performs interactive smoke testing
- **THEN** docs include a manual check that enabling hardware cursor positioning places IME/cursor affordances at the focused editor/input cursor
