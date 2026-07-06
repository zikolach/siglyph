# developer-api Specification

## Purpose
Defines the public project architecture, dependency constraints, cross-platform module model, documentation expectations, demo commands, and API compatibility principles for scala-tui.
## Requirements
### Requirement: Dependency-light public core
The library SHALL provide a public core module whose component, rendering, ANSI utility, key model, and test-support APIs do not require Node.js or npm packages. Third-party runtime dependencies MUST be added only after explicit confirmation.

#### Scenario: Core compiles without Node.js
- **WHEN** the core module is compiled on a machine without Node.js installed
- **THEN** compilation does not require a JavaScript runtime or npm package resolution

#### Scenario: Runtime dependency requires approval
- **WHEN** a runtime dependency is proposed for any module
- **THEN** the dependency name, purpose, target platforms, and alternatives are documented before it is added

### Requirement: Mill Scala 3 modular architecture
The library SHALL be named `scala-tui`, use latest stable Mill with Scala 3 modules, and SHALL separate pure rendering and component logic from platform terminal backends so backend implementations can vary by target while preserving a stable application API.

#### Scenario: Application chooses backend
- **WHEN** an application creates a TUI
- **THEN** it can pass a terminal backend implementation without changing component code

#### Scenario: Bootstrap package namespace
- **WHEN** the first implementation slice is created
- **THEN** public Scala sources use the `scalatui` package namespace unless a final publishing namespace has been chosen

### Requirement: pi-tui compatibility intent
The library SHALL use current `pi-tui` behavior as the canonical reference for feature semantics while allowing Scala-idiomatic types and APIs.

#### Scenario: Compatibility test uses upstream fixture
- **WHEN** a behavior is ported from `pi-tui`
- **THEN** the Scala test suite includes either a direct scenario equivalent or a documented intentional deviation

### Requirement: TauTUI reference usage
The project SHALL treat TauTUI as a Node-free architectural reference but MUST verify behavior against current `pi-tui` before claiming parity.

#### Scenario: TauTUI differs from current pi-tui
- **WHEN** TauTUI and current `pi-tui` disagree on behavior
- **THEN** the implementation follows current `pi-tui` unless a deviation is documented in the design or spec

### Requirement: Unicode table generation
The library SHALL generate Unicode display-width and grapheme-segmentation tables from the latest Unicode data using a Scala CLI script and commit the generated tables to the repository.

#### Scenario: Generated Unicode version is recorded
- **WHEN** Unicode tables are generated
- **THEN** the generated source records the Unicode data version and source URLs used

#### Scenario: Unicode tables are reproducible
- **WHEN** the Unicode generation script is run with the same Unicode data version
- **THEN** it reproduces the committed table contents

### Requirement: Test harness and regression suite
The library SHALL include tests for rendering, terminal protocol parsing, Unicode width and editing, autocomplete, components, and virtual-terminal integration before a capability is considered complete. Test-only dependencies are allowed.

#### Scenario: Renderer regression test
- **WHEN** the renderer changes
- **THEN** normalized virtual-viewport tests verify semantic output and targeted raw ANSI snapshot tests verify escape-stream compatibility for first render, partial render, full redraw, width change, and overlay composition behavior

#### Scenario: Unicode editing regression test
- **WHEN** input or editor behavior changes
- **THEN** tests verify ASCII, CJK, combining marks, emoji, ANSI-adjacent text, and paste handling

### Requirement: Interactive run API
The public API SHALL provide a simple way to run an interactive TUI until the application requests exit.

#### Scenario: Application runs until exit request
- **WHEN** an application starts the TUI with the interactive run API
- **THEN** the call remains active until the application requests exit or the terminal runtime fails

#### Scenario: Application requests exit
- **WHEN** application code calls the exit-request API from an input handler
- **THEN** the interactive run loop exits and terminal state is restored

### Requirement: Shared demo construction
The project SHALL provide a shared demo UI construction path that can be used by both JVM and Scala Native interactive demo launchers.

#### Scenario: Shared demo UI is used by JVM and Native
- **WHEN** JVM and Native demos are built
- **THEN** they use the same component tree and interaction logic except for backend selection

### Requirement: Interactive demo commands
The Mill build SHALL expose commands or targets for running the JVM interactive demo and building/running the Scala Native interactive demo.

#### Scenario: JVM demo command
- **WHEN** a developer runs the JVM interactive demo target in a TTY
- **THEN** the demo starts without requiring manual terminal setup

#### Scenario: Native demo command
- **WHEN** a developer builds or runs the Native interactive demo target
- **THEN** the target uses the Scala Native terminal backend and documents any required invocation steps

### Requirement: No new runtime dependencies
This change SHALL NOT add new third-party runtime dependencies.

#### Scenario: Dependency list remains unchanged
- **WHEN** the Mill build is inspected after implementation
- **THEN** no new runtime dependency is present beyond the already-approved Scala Native and test-only dependencies

### Requirement: Minimal input handling result
The public component API SHALL provide a minimal way for input handling to report whether input was handled and whether the runtime should perform standard follow-up actions such as rendering or exit.

#### Scenario: Component reports handled input
- **WHEN** a focused component handles a terminal input event
- **THEN** the runtime can observe that the event was handled without inspecting component internals

#### Scenario: Component requests no redundant render
- **WHEN** a component receives input that does not change visible state
- **THEN** the runtime can avoid scheduling a redundant render for that input event

#### Scenario: Component requests exit
- **WHEN** component input handling requests application exit
- **THEN** the interactive run loop exits and terminal state is restored through the existing shutdown path

### Requirement: Public model APIs remain backend-independent
New editing model APIs SHALL live in the shared core and SHALL NOT depend on JVM-only, Native-only, terminal backend, or rendering implementation details.

#### Scenario: Editor buffer compiles for JVM and Native core
- **WHEN** the editor buffer model is compiled for JVM and Scala Native core modules
- **THEN** it compiles without platform-specific APIs or runtime dependencies

### Requirement: No new runtime dependencies for editor foundation
The editor-buffer and API-refinement change SHALL NOT add third-party runtime dependencies.

#### Scenario: Dependency list remains unchanged
- **WHEN** the Mill build is inspected after this change
- **THEN** no new runtime dependency is present beyond already-approved Scala Native and test-only dependencies

### Requirement: Documentation and Scaladoc for public APIs
Changes that add or modify public APIs SHALL include project documentation and Scaladoc describing contract, platform scope, and important non-goals.

#### Scenario: Public API documentation accompanies implementation
- **WHEN** a change adds a public type or public method intended for application use
- **THEN** the implementation includes Scaladoc and the project documentation explains the capability or explicitly records why no documentation update is needed

#### Scenario: Future changes preserve documentation expectations
- **WHEN** a future OpenSpec change proposes public API or user-visible behavior
- **THEN** its tasks include documentation and Scaladoc work before validation is considered complete

### Requirement: Formatting and best-practice lint configuration
The project SHALL include Scalafmt and Scalafix configuration so contributors and automation can check formatting and baseline Scala best-practice rules.

#### Scenario: Formatting configuration exists
- **WHEN** a contributor wants to check source formatting
- **THEN** the repository provides Scalafmt configuration and documented commands for formatting or checking formatting

#### Scenario: Scalafix configuration exists
- **WHEN** a contributor wants to check baseline Scala best-practice rules
- **THEN** the repository provides Scalafix configuration and documented commands for running those checks

### Requirement: Public editor API
The public core API SHALL expose a Scala-idiomatic multiline editor component and options without adding runtime dependencies.

#### Scenario: Application creates editor
- **WHEN** an application creates an editor with initial text and options
- **THEN** it can add the editor to a TUI component tree without platform-specific code

#### Scenario: Editor API compiles for JVM and Native core
- **WHEN** the editor component and options are compiled for JVM and Scala Native core modules
- **THEN** they compile without JVM-only, Native-only, or third-party runtime dependencies

### Requirement: Editor Enter behavior API
The public API SHALL expose a typed configuration for editor Enter behavior.

#### Scenario: Submit on Enter configuration
- **WHEN** an application configures the editor for submit-on-Enter behavior
- **THEN** it can configure Shift+Enter as a newline input without raw escape strings

#### Scenario: Newline on Enter configuration
- **WHEN** an application configures the editor for newline-on-Enter behavior
- **THEN** it can configure Cmd/Super+Enter as a submit input without raw escape strings

### Requirement: Editor API documentation
The editor public APIs SHALL include Scaladoc and project documentation covering behavior, keyboard controls, and non-goals.

#### Scenario: Scaladoc documents editor contracts
- **WHEN** public editor types or options are added
- **THEN** their Scaladoc explains mutation delegation, Enter behavior, callbacks, and platform scope

#### Scenario: Demo documentation lists controls
- **WHEN** the editor demo is added
- **THEN** README or project docs list its launch commands and key controls

### Requirement: Editor validation includes quality checks
The editor demo change SHALL include formatting, lint, test, and Native build validation.

#### Scenario: Mill quality validation
- **WHEN** the change is complete
- **THEN** `mill quality`, `mill __.compile`, `mill core.test`, and Native editor demo build validation pass

### Requirement: Native modules reuse shared sources through Mill configuration
Scala Native mirror modules SHALL reuse shared JVM/Native implementation sources through Mill source-root configuration rather than symlinked source directories.

#### Scenario: Core Native compiles canonical core sources
- **WHEN** the `coreNative` module is compiled
- **THEN** it compiles the canonical `core/src` shared source tree without requiring a `coreNative/src` symlink

#### Scenario: Interactive demo Native compiles canonical demo sources
- **WHEN** the `interactiveDemoNative` module is compiled
- **THEN** it compiles the canonical `interactiveDemo/src` shared source tree without requiring an `interactiveDemoNative/src` symlink

#### Scenario: Repository layout avoids symlink mirrors
- **WHEN** a contributor inspects the repository source directories
- **THEN** shared JVM/Native implementations appear in one canonical source tree and Native-specific modules do not expose symlink mirrors of that tree

### Requirement: Shared source root cleanup preserves behavior
Replacing source symlinks with Mill shared source roots SHALL preserve module graph, public APIs, and dependency constraints.

#### Scenario: JVM and Native modules still compile
- **WHEN** the source-root cleanup is complete
- **THEN** `mill __.compile` succeeds for JVM and Scala Native modules

#### Scenario: Native interactive demo still links
- **WHEN** the source-root cleanup is complete
- **THEN** `mill interactiveNativeDemo.nativeLink` succeeds using the same shared demo implementation

#### Scenario: No runtime dependency is added
- **WHEN** the Mill build is inspected after this cleanup
- **THEN** no new third-party runtime dependency is present

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

### Requirement: Public TUI context API
The public core API SHALL expose a backend-independent TUI context or host capability for components that need runtime services such as render requests, exit requests, focus changes, and overlay operations.

#### Scenario: Component requests render through context
- **WHEN** a component receives asynchronous state from a provider after input handling has returned
- **THEN** it can request a render through the TUI context without depending on a concrete terminal backend

#### Scenario: Component requests exit through context
- **WHEN** a component action decides the application should exit
- **THEN** it can request exit through the TUI context and the existing runtime shutdown path restores terminal state

#### Scenario: Context is testable
- **WHEN** a component using runtime services is tested without a real terminal
- **THEN** a fake context can record render, exit, focus, and overlay operations

### Requirement: Public overlay API
The public core API SHALL expose Scala-idiomatic overlay types, overlay options, overlay handles, anchors, margins, and size values without adding third-party runtime dependencies.

#### Scenario: Application creates centered overlay
- **WHEN** an application shows a component overlay without explicit position options
- **THEN** it can use the returned handle to manage a centered overlay through public APIs

#### Scenario: Application creates positioned overlay
- **WHEN** an application supplies width, maximum height, row, column, anchor, offset, margin, visibility, or focus options
- **THEN** those options are expressed using typed Scala values rather than raw terminal escape strings

#### Scenario: Overlay API compiles for JVM and Native core
- **WHEN** the overlay API is compiled by JVM core and Scala Native core modules
- **THEN** it compiles without JVM-only, Native-only, Node.js, or third-party runtime dependencies

### Requirement: Public autocomplete API
The public core API SHALL expose autocomplete provider, request, result, item, suggestion, cancellable handle, and callback types that are usable on both JVM and Scala Native.

#### Scenario: Application supplies provider
- **WHEN** an application configures an editor with an autocomplete provider
- **THEN** it can do so using shared core types without choosing a terminal backend-specific implementation

#### Scenario: Provider can wrap external effect runtime
- **WHEN** an application has file, network, Future, or effect-runtime based autocomplete logic
- **THEN** it can bridge that logic into the callback provider contract without requiring the TUI library to depend on that effect runtime

### Requirement: Overlay and autocomplete documentation
New public overlay, context, and autocomplete APIs SHALL include Scaladoc and project documentation describing behavior, platform scope, keyboard controls, and non-goals.

#### Scenario: Scaladoc documents public contracts
- **WHEN** public overlay, context, or autocomplete types are added
- **THEN** their Scaladoc explains ownership, lifecycle, cancellation, rendering constraints, and dependency expectations

#### Scenario: Project docs describe demo controls
- **WHEN** the interactive demo gains overlay autocomplete behavior
- **THEN** README or smoke documentation lists launch commands and keyboard controls for opening, navigating, accepting, and cancelling suggestions

### Requirement: Editor autocomplete placement API
The public editor API SHALL expose a Scala-idiomatic way to use built-in editor-adjacent autocomplete placement while preserving explicit overlay placement overrides.

#### Scenario: Default placement requires no row arithmetic
- **WHEN** an application creates an editor with an autocomplete provider and default placement settings
- **THEN** it does not need to compute terminal row or column values to place suggestions next to the editor

#### Scenario: Application can override placement
- **WHEN** an application needs fixed, terminal-relative, or custom overlay positioning for editor autocomplete
- **THEN** it can configure explicit overlay placement options using public core types

#### Scenario: Placement API is backend-independent
- **WHEN** editor autocomplete placement APIs compile for JVM and Scala Native core modules
- **THEN** they compile without JVM-only, Native-only, Node.js, or third-party runtime dependencies

#### Scenario: Placement API is documented
- **WHEN** public editor autocomplete placement types or methods are added or changed
- **THEN** their Scaladoc documents default adjacent placement, custom override behavior, and important non-goals

### Requirement: Public API for advanced text-editing capabilities
The public component APIs SHALL expose configuration and callback points needed to use undo, kill-ring, yank/yank-pop, and large-paste marker behavior without accessing runtime internals.

#### Scenario: Input API exposes advanced command hooks
- **WHEN** an application constructs and uses an `Input`
- **THEN** it can observe or invoke advanced history behavior through documented callbacks/settings on the public `Input` API

#### Scenario: Editor API preserves backward compatibility
- **WHEN** existing editor construction code is compiled
- **THEN** new editing APIs are additive and do not break existing callback signatures or core module compatibility

### Requirement: Public image APIs are stable and dependency-light
The developer API SHALL expose core image-related capability data types and optional image-module renderer helpers in a way that does not require platform-specific dependencies at call sites.

#### Scenario: Applications can construct image options and helpers
- **WHEN** an application depends on the optional image module and configures image rendering options
- **THEN** it can do so from `scalatui` public types only, without JVM/Native-specific import branches

#### Scenario: Public API does not force terminal backend coupling
- **WHEN** applications run on JVM or Scala Native
- **THEN** image types and markdown/autocomplete models remain usable without depending on concrete terminal backend classes

#### Scenario: Core-only applications avoid image dependencies
- **WHEN** an application does not opt into image rendering modules
- **THEN** it can use `core` terminal capability APIs without transitively depending on image decoding, scaling, or media libraries

### Requirement: Public markdown/autocomplete contracts are composable
The markdown and autocomplete contracts SHALL remain composable with existing `Component`, `TUI`, and effect runtimes.

#### Scenario: Provider composition without effect runtime dependencies
- **WHEN** an application bridges a future/callback/file-system source into autocomplete
- **THEN** it can do so through the documented provider contract without adding runtime dependencies to `scala-tui`

#### Scenario: Markdown usage stays within component contract
- **WHEN** an application uses markdown rendering
- **THEN** it does so through the shared component-style output contract and standard width constraints

### Requirement: Public alternate-screen runtime option
The public core API SHALL expose a Scala-idiomatic opt-in option for running a `TUI` in terminal alternate-screen mode without changing existing defaults or component contracts.

#### Scenario: Existing TUI construction remains normal-screen
- **WHEN** existing application code constructs `TUI(terminal)` or `TUI(terminal, TUIOptions())`
- **THEN** the TUI runs in normal-screen mode and existing source remains valid

#### Scenario: Application opts into alternate screen through TUI options
- **WHEN** an application configures alternate-screen mode through public `TUIOptions`
- **THEN** the shared TUI runtime enters alternate screen for that TUI lifecycle without requiring application code to emit raw terminal escape strings

#### Scenario: Component API remains width-only
- **WHEN** alternate-screen mode is enabled
- **THEN** components still render through `Component.render(width): Vector[String]` without requiring a height-aware component API

#### Scenario: API compiles on JVM and Native core
- **WHEN** the alternate-screen option is compiled for JVM core and Scala Native core modules
- **THEN** it compiles without JVM-only, Native-only, or third-party runtime dependencies

### Requirement: Alternate-screen documentation
The project documentation and Scaladoc SHALL describe alternate-screen mode, its opt-in default, cleanup behavior, and non-goals.

#### Scenario: Documentation explains normal-screen default
- **WHEN** a developer reads TUI runtime documentation
- **THEN** it states that normal-screen mode is the default and existing applications do not enter alternate screen unless configured

#### Scenario: Documentation explains scrollback behavior
- **WHEN** a developer reads alternate-screen documentation
- **THEN** it explains that alternate-screen mode prevents TUI frames from being appended to normal shell scrollback while the TUI is running

#### Scenario: Documentation lists non-goals
- **WHEN** a developer reads alternate-screen documentation
- **THEN** it states that temporary modal alternate-screen sessions, a full-screen editor, and height-aware component rendering are not provided by this change

### Requirement: Public hardware cursor positioning option
The public core API SHALL expose a backend-independent option for enabling or disabling marker-driven hardware cursor positioning without requiring application code to emit raw terminal escape sequences.

#### Scenario: Default preserves existing cursor behavior
- **WHEN** an application constructs a TUI without configuring hardware cursor positioning
- **THEN** fake cursor rendering remains visible and no marker-derived hardware cursor movement is performed

#### Scenario: Application opts in through public API
- **WHEN** an application enables hardware cursor positioning through the public TUI options
- **THEN** the shared TUI runtime uses focused editing cursor markers to place the terminal hardware cursor where supported by the backend abstraction

#### Scenario: API compiles on JVM and Native core
- **WHEN** the hardware cursor positioning option is compiled for JVM core and Scala Native core modules
- **THEN** it does not depend on JVM-only, Native-only, Node.js, or third-party runtime APIs

### Requirement: Hardware cursor positioning documentation
The project documentation SHALL describe the hardware cursor positioning option, its opt-in default, fake-cursor preservation, and testing expectations.

#### Scenario: Documentation lists behavior and caveats
- **WHEN** a developer reads TUI runtime or editor documentation
- **THEN** it explains that marker-driven hardware cursor positioning is opt-in, markers are stripped from output, and fake cursor rendering is preserved

#### Scenario: Smoke docs include IME/cursor checks
- **WHEN** a developer performs interactive smoke testing
- **THEN** docs include a manual check that enabling hardware cursor positioning places IME/cursor affordances at the focused editor/input cursor

### Requirement: Public configurable keybinding API
The public core API SHALL expose Scala-idiomatic keybinding command ids, key descriptors, defaults, override configuration, and conflict diagnostics without adding runtime dependencies or platform-specific imports.

#### Scenario: Application configures keybindings without backend imports
- **WHEN** an application creates an editor or TUI with custom keybindings
- **THEN** it can use shared `scalatui` core types only, regardless of JVM or Scala Native backend selection

#### Scenario: Existing construction remains source-compatible
- **WHEN** existing applications construct `Input`, `Editor`, or `TUI` without specifying keybindings
- **THEN** they compile and use the default keybindings

#### Scenario: Public API documents command scope
- **WHEN** a developer inspects keybinding command ids
- **THEN** the API distinguishes editor, input, and selection/autocomplete command scopes and documents how custom overrides are resolved

### Requirement: Keybinding parity documentation
Project documentation and Scaladoc SHALL list default editor/input/autocomplete keybindings, customization behavior, and known terminal/parser deviations.

#### Scenario: Docs list default controls
- **WHEN** a developer reads README or interactive smoke documentation
- **THEN** it lists the default controls for editor movement, history, page movement, jump mode, undo, kill-ring/yank, autocomplete navigation, submit, newline, and exit/cancel behavior

#### Scenario: Docs describe unsupported combinations
- **WHEN** a default upstream binding cannot be represented reliably on all supported terminals
- **THEN** docs name the affected binding and describe the closest supported behavior or configuration workaround

#### Scenario: Scaladoc covers public keybinding models
- **WHEN** public keybinding types or methods are added
- **THEN** their Scaladoc explains backend independence, override semantics, conflict diagnostics, and dependency constraints

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

### Requirement: Public ANSI geometry behavior is documented when clarified
If this change clarifies or exposes public ANSI geometry behavior, the public API SHALL document tab width, wide-grapheme slicing behavior, and width-safety expectations.

#### Scenario: Public tab behavior is documented
- **WHEN** tab width is part of public helper behavior
- **THEN** Scaladoc and project documentation state the tab display width used by the helper

#### Scenario: Public slicing behavior is documented
- **WHEN** a public helper slices or truncates terminal text
- **THEN** Scaladoc explains that ANSI escape sequences are non-printing and wide grapheme clusters are not emitted partially

#### Scenario: No public API change needs no new API docs
- **WHEN** geometry hardening changes only internal behavior and tests
- **THEN** no new public helper documentation is required beyond updated regression notes if relevant

### Requirement: Typed global input listener API
The public TUI API SHALL allow applications to register typed global input listeners that observe terminal input before the focused component receives it.

#### Scenario: Listener observes typed input first
- **WHEN** a terminal input event is received and a global input listener is registered
- **THEN** the listener receives the typed `TerminalInput` before the focused component input handler is invoked

#### Scenario: Ignored listener allows focused routing
- **WHEN** every global input listener reports ignored input
- **THEN** the TUI routes the input to the focused component using the existing focused input path

#### Scenario: Handled listener stops focused routing
- **WHEN** a global input listener reports handled input
- **THEN** the TUI does not route that same input event to the focused component

#### Scenario: Listener can request exit
- **WHEN** a global input listener returns the input result for application exit
- **THEN** the TUI exits through the existing shutdown path and restores terminal state

### Requirement: Public editor programmatic insertion API
The public editor API SHALL expose a Scala-idiomatic method for inserting application-supplied text at the current cursor without requiring applications to synthesize terminal input.

#### Scenario: Application inserts text programmatically
- **WHEN** application code calls the editor insertion API with text
- **THEN** the editor inserts that text at the current cursor using the same logical buffer rules as editor-owned insertion

#### Scenario: Programmatic insertion is documented
- **WHEN** the editor insertion API is added
- **THEN** Scaladoc and project documentation describe callbacks, undo behavior, paste normalization, autocomplete refresh behavior, and render behavior

### Requirement: API parity documentation
The project documentation SHALL record the selected `pi-tui` parity gaps closed by this change and any intentional default differences.

#### Scenario: Porting notes describe listener parity
- **WHEN** the change is complete
- **THEN** `docs/porting-notes.md` describes typed global input listeners as the siglyph counterpart to `pi-tui` raw input listeners

#### Scenario: Porting notes describe autocomplete default
- **WHEN** forced single-completion auto-apply is implemented as opt-in behavior
- **THEN** `docs/porting-notes.md` states that siglyph preserves explicit selection by default and enables auto-apply only when configured

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

### Requirement: Java and Kotlin JVM interop API
The developer API SHALL provide a JVM-only interop layer that lets Java and Kotlin applications create a basic interactive siglyph TUI without calling Scala-generated default-argument methods.

#### Scenario: Java application creates a basic TUI
- **WHEN** a Java application depends on the JVM siglyph artifacts and uses the interop API to create a terminal-backed TUI with `Text` and `Input` components
- **THEN** the Java source compiles without calling Scala-generated `$lessinit$greater$default$N` methods

#### Scenario: Kotlin application creates a basic TUI
- **WHEN** a Kotlin application depends on the JVM siglyph artifacts and uses the interop API to create a terminal-backed TUI with `Text` and `Input` components
- **THEN** the Kotlin source compiles without calling Scala-generated `$lessinit$greater$default$N` methods

### Requirement: JVM interop boundary uses JDK types for common callbacks
The JVM interop API SHALL expose common callbacks through standard JDK functional interfaces so Java and Kotlin applications do not need to construct Scala function types for basic input submission.

#### Scenario: Java input submit callback uses JDK type
- **WHEN** a Java application registers an input submit callback through the interop API
- **THEN** the callback type is a standard JDK functional interface

#### Scenario: Kotlin input submit callback uses Kotlin lambda syntax
- **WHEN** a Kotlin application registers an input submit callback through the interop API
- **THEN** the callback can be supplied with Kotlin lambda syntax without referencing `scala.Function1`

### Requirement: JVM interop keeps Scala APIs and Native APIs unchanged
The JVM interop layer SHALL be additive and SHALL NOT change existing Scala-first APIs or add Java/Kotlin support promises for Scala Native artifacts.

#### Scenario: Existing Scala construction remains valid
- **WHEN** an existing Scala application constructs `TUI`, `Text`, or `Input` through current public constructors
- **THEN** the existing source continues to compile without using the JVM interop layer

#### Scenario: Native module API surface is not expanded for JVM language interop
- **WHEN** Scala Native modules are compiled after this change
- **THEN** they do not require JVM-only interop sources or Java/Kotlin compiler tooling

### Requirement: JVM interop documentation and validation
The project SHALL document JVM Java and Kotlin usage and validate the documented call shape with compile checks.

#### Scenario: Documentation includes dependency coordinates and examples
- **WHEN** a Java or Kotlin developer reads the project documentation
- **THEN** it lists JVM dependency coordinates and provides minimal Java and Kotlin examples for creating and running a basic TUI

#### Scenario: Java and Kotlin call shapes are validated
- **WHEN** the relevant JVM validation target runs
- **THEN** Java and Kotlin smoke sources that use the documented interop API compile successfully

### Requirement: JVM interop adds no runtime dependencies
The JVM interop change SHALL NOT add third-party runtime dependencies.

#### Scenario: Runtime dependency list remains unchanged
- **WHEN** the Mill build is inspected after the JVM interop implementation
- **THEN** no new runtime dependency is present beyond the existing approved modules and dependencies
