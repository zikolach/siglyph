## MODIFIED Requirements

### Requirement: Test harness and regression suite
The library SHALL include tests for rendering, terminal protocol parsing, Unicode width and editing, autocomplete, components, and virtual-terminal integration before a capability is considered complete. The project SHALL retain the fast deterministic `VirtualTerminal` fake and SHALL supplement it with focused PTY or emulator-style conformance coverage on Linux and macOS for behavior the fake cannot model accurately. Test-only dependencies are allowed.

#### Scenario: Renderer regression test
- **WHEN** the renderer changes
- **THEN** normalized virtual-viewport tests verify semantic output and targeted raw ANSI snapshot tests verify escape-stream compatibility for first render, partial render, full redraw, width change, and overlay composition behavior

#### Scenario: Unicode editing regression test
- **WHEN** input or editor behavior changes
- **THEN** tests verify ASCII, CJK, combining marks, emoji, ANSI-adjacent text, and paste handling

#### Scenario: Virtual terminal remains a fast unit fake
- **WHEN** ordinary runtime and component tests need deterministic input, resize, query, or output behavior
- **THEN** they can use `VirtualTerminal` without starting a real terminal emulator or subprocess

#### Scenario: PTY conformance covers terminal semantics
- **WHEN** a behavior depends on wide-cell occupancy, autowrap, erasure, cursor placement, resize, or terminal restoration semantics that `VirtualTerminal` does not fully model
- **THEN** focused integration coverage executes through a PTY or equivalent real terminal boundary on a supported CI runner

#### Scenario: Linux and macOS quality gates run
- **WHEN** continuous integration validates a pull request
- **THEN** Linux and macOS jobs compile and test the relevant JVM and Scala Native terminal paths, with any platform-specific validation gap stated explicitly in the workflow

## ADDED Requirements

### Requirement: Loader cancellation is thread-safe
The public `CancellationToken` and `CancellableLoader.cancel()` contract SHALL provide cross-thread visibility, idempotent atomic cancellation, and at-most-once cancellation callback invocation on JVM and Scala Native without adding an effect runtime.

#### Scenario: Worker observes cancellation
- **WHEN** one thread or Native worker polls a token and another execution context successfully cancels it
- **THEN** the polling code can observe the cancelled state according to the documented memory-visibility contract

#### Scenario: Concurrent cancellation invokes callback once
- **WHEN** multiple callers invoke `cancel()` concurrently
- **THEN** exactly one call reports successful cancellation and `onCancel` runs at most once

#### Scenario: Repeated cancellation is idempotent
- **WHEN** cancellation is invoked again after the token is already cancelled
- **THEN** it reports no state transition, invokes no additional callback, and does not request an additional cancellation render

#### Scenario: Cancellation remains dependency-free
- **WHEN** thread-safe cancellation is implemented for shared JVM and Native sources
- **THEN** it uses standard shared-platform primitives or a small compatibility boundary and adds no third-party runtime dependency

### Requirement: Public terminal diagnostics and resize policy are source-compatible
The public TUI configuration SHALL expose optional instance-scoped terminal diagnostics and normal-screen resize clear policy without adding required abstract members to existing terminal implementations or changing default application behavior.

#### Scenario: Existing TUI construction retains behavior
- **WHEN** existing source constructs `TUIOptions` without the new fields
- **THEN** it compiles unchanged, emits no diagnostics, and retains full-clear normal-screen resize behavior

#### Scenario: Diagnostics types are backend-independent
- **WHEN** an application configures a diagnostic observer
- **THEN** it uses shared public types without importing JVM, Native, or concrete terminal-backend APIs

#### Scenario: Resize policy is documented
- **WHEN** a developer reads the public resize option documentation
- **THEN** it explains viewport clearing, scrollback preservation, alternate-screen behavior, and the default compatibility choice

#### Scenario: Diagnostic privacy is documented
- **WHEN** a developer reads the observer Scaladoc
- **THEN** it states which bounded metadata is exposed, that application content is excluded by default, and how observer failures are contained

### Requirement: Current pi-tui compatibility documentation
Project documentation SHALL maintain a version-pinned compatibility matrix for the major `pi-tui` subsystems and SHALL distinguish full behavioral parity, partial parity, intentional deviation, and Siglyph extensions.

#### Scenario: Compatibility claim is traceable
- **WHEN** a developer reads the compatibility matrix
- **THEN** it identifies the reviewed upstream repository revision or package version and links each major status to local tests, specs, or documented limitations

#### Scenario: Intentional deviations are explicit
- **WHEN** Siglyph uses typed input/output, dependency-free Markdown, Scala Native, callback cancellation, or coordinate-aware mouse behavior that differs from upstream
- **THEN** the matrix describes the deviation without presenting shared component names as proof of behavioral parity

#### Scenario: Review-discovered documentation is consistent
- **WHEN** README, smoke instructions, porting notes, roadmap notes, and OpenSpec references describe a reviewed feature
- **THEN** examples and active-change references agree with implemented defaults and the current OpenSpec state
