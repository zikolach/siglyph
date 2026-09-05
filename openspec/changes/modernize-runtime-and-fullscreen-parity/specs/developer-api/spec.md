## MODIFIED Requirements

### Requirement: Public alternate-screen runtime option
The public core API SHALL expose Scala-idiomatic opt-in APIs for both alternate-screen buffer lifecycle and a height-aware fullscreen viewport while preserving normal-screen defaults and existing source. Existing width-only components SHALL remain valid leaves under either mode.

#### Scenario: Existing TUI construction remains normal-screen
- **WHEN** existing application code constructs `TUI(terminal)` or `TUI(terminal, TUIOptions())`
- **THEN** the TUI runs in normal-screen mode and existing source remains valid

#### Scenario: Existing alternate-screen option remains valid
- **WHEN** existing application code configures `TUIOptions(screenMode = TUIScreenMode.Alternate)`
- **THEN** it continues to enter alternate screen with its documented width-only behavior without requiring a viewport layout root

#### Scenario: Application opts into fullscreen viewport
- **WHEN** an application explicitly constructs or configures the height-aware viewport renderer
- **THEN** it can supply a layout root and use application-owned scrolling without emitting raw terminal escape strings

#### Scenario: Width-only component remains valid
- **WHEN** either alternate-screen mode renders a component that only implements `Component.render(width): ComponentRender`
- **THEN** the component remains source-compatible and acts as an intrinsic-height leaf in viewport layout

#### Scenario: API compiles on JVM and Native core
- **WHEN** alternate-screen and viewport APIs are compiled for JVM core and Scala Native core modules
- **THEN** they compile without JVM-only, Native-only, Node.js, or third-party runtime dependencies

### Requirement: Alternate-screen documentation
Project documentation and Scaladoc SHALL distinguish normal-screen rendering, alternate-screen buffer lifecycle, and height-aware fullscreen viewport rendering. It SHALL document opt-in defaults, cleanup, layout, scrolling, mouse capture, selection, and fallback behavior.

#### Scenario: Documentation explains normal-screen default
- **WHEN** a developer reads TUI runtime documentation
- **THEN** it states that normal-screen mode is the default and existing applications do not enter alternate screen unless configured

#### Scenario: Documentation explains both alternate-screen paths
- **WHEN** a developer reads alternate-screen documentation
- **THEN** it distinguishes buffer-only alternate-screen lifecycle from the height-aware fullscreen viewport and gives construction examples for each

#### Scenario: Documentation lists non-goals
- **WHEN** a developer reads fullscreen viewport documentation
- **THEN** it states that runtime renderer switching, Windows support, Node scheduling, mandatory host clipboard integration, and built-in LaTeX parsing are not provided by this change

## ADDED Requirements

### Requirement: Public component threading contract
The public API SHALL state which component operations may be called from application execution contexts, how those operations are serialized with rendering, and that application callbacks run outside component and runtime locks. Built-in components SHALL follow the same contract on JVM and Scala Native.

#### Scenario: Scheduler drives loader
- **WHEN** an application-owned scheduler drives `Loader.tick()` while the TUI is active
- **THEN** documented behavior explains the safe call path and the runtime observes ordered visible state

#### Scenario: Callback lock behavior is documented
- **WHEN** a developer reads Editor, Loader, autocomplete, or TUI callback Scaladoc
- **THEN** it states that application code runs outside library state and terminal-output locks and describes callback ordering

### Requirement: Version-pinned compatibility claims
Every project claim about current `pi-tui` feature or behavior parity SHALL name one reviewed upstream commit and date. The compatibility matrix SHALL list added upstream areas, local coverage, intentional deviations, and evidence, and SHALL be refreshed before a release repeats a completeness claim.

#### Scenario: Matrix uses reviewed revision
- **WHEN** compatibility documentation is published for this change
- **THEN** it names `earendil-works/pi` commit `da840b6216578c2a571d0374ac6a2091a83f9d91` or a later explicitly reviewed commit and compares against that exact tree

#### Scenario: README does not overstate coverage
- **WHEN** an upstream component category or behavior is not implemented locally
- **THEN** README and compatibility documentation name the gap instead of claiming complete current coverage

#### Scenario: Intentional deviation remains explicit
- **WHEN** Siglyph keeps a typed, Node-free, dependency-light, or platform-scoped alternative
- **THEN** the matrix records the deviation without classifying source incompatibility alone as a defect

### Requirement: Deterministic performance benchmarks
The project SHALL provide repeatable benchmark scenarios for large transcripts, differential redraw, Unicode wrapping, overlays, viewport scrolling, search, and image-heavy frames. Benchmarks SHALL report workload size and measured time or allocation data without becoming runtime dependencies or mandatory correctness tests.

#### Scenario: Large transcript benchmark is repeatable
- **WHEN** a maintainer runs the documented large-transcript benchmark with the same revision and workload parameters
- **THEN** it reports comparable render and scroll measurements with the exact workload size

#### Scenario: Benchmark detects full-document repaint
- **WHEN** a one-row viewport change over a large unchanged transcript causes work proportional to the full document
- **THEN** benchmark evidence exposes the regression before parity is claimed complete

#### Scenario: Normal validation remains fast
- **WHEN** contributors run ordinary unit tests and formatting checks
- **THEN** performance benchmarks do not run unless their dedicated target is selected

### Requirement: Fullscreen public API documentation
New stack, scroll-view, search, selection, mouse-region, capability-override, and renderer-policy APIs SHALL include Scaladoc, examples, JVM and Native scope, ownership, bounds, callback, and important non-goal documentation.

#### Scenario: Fullscreen example is complete
- **WHEN** a developer reads the fullscreen quick-start example
- **THEN** it shows a growing transcript, primary follow-end scroll view, editor or footer region, viewport construction, and safe lifecycle shutdown

#### Scenario: Public test utilities cover viewport
- **WHEN** an adopter tests a fullscreen component tree with `VirtualTerminal`
- **THEN** public test APIs can drive dimensions, mouse gestures, scrolling, search, and inspect the committed viewport without a real TTY
