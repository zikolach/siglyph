## MODIFIED Requirements

### Requirement: Height-aware resize redraws
The TUI runtime SHALL track both terminal width and terminal height changes across renders and SHALL repaint after dimension changes according to the active screen mode and configured normal-screen resize clear policy. Existing callers that do not configure the policy SHALL retain full-clear behavior.

#### Scenario: Default normal-screen width resize redraws with full clear
- **WHEN** terminal width changes after a previous render in normal-screen mode with default options
- **THEN** the TUI emits synchronized output with autowrap disabled, clears the viewport and scrollback with `CSI 2 J`, `CSI H`, and `CSI 3 J`, and writes the recomputed frame without entering alternate screen

#### Scenario: Default normal-screen height resize redraws with full clear
- **WHEN** terminal height changes after a previous render in normal-screen mode with default options
- **THEN** the TUI emits synchronized output with autowrap disabled, clears the viewport and scrollback with `CSI 2 J`, `CSI H`, and `CSI 3 J`, and writes the recomputed frame without entering alternate screen

#### Scenario: Preserve-scrollback resize omits scrollback clearing
- **WHEN** terminal dimensions change in normal-screen mode with the preserve-scrollback policy configured
- **THEN** the TUI clears and homes the active viewport, omits `CSI 3 J`, and writes the recomputed frame without entering alternate screen

#### Scenario: Resize with overlay recomputes layout
- **WHEN** terminal dimensions change while an autocomplete overlay is visible in normal-screen mode
- **THEN** the overlay is re-resolved and composited into the resize redraw using the configured normal-screen clear policy without entering alternate-screen mode

#### Scenario: Alternate-screen resize redraw clears active viewport
- **WHEN** terminal dimensions change after a previous render while alternate-screen mode is active
- **THEN** the TUI emits synchronized output with autowrap disabled, clears the active alternate-screen viewport, homes the cursor, and writes the recomputed frame without emitting another alternate-screen enter sequence or `CSI 3 J`

### Requirement: Typed input coverage for keybinding parity
The terminal input model and shared parser SHALL support the typed key events needed by the default editor, input, and selection keybindings plus commonly reported function, cursor, keypad, modifier, and control sequences where those events can be recognized portably. Unknown or genuinely ambiguous sequences SHALL remain representable as bounded raw input.

#### Scenario: Page keys are parsed
- **WHEN** a terminal sends common PageUp or PageDown escape sequences
- **THEN** the runtime emits typed key events that can match `tui.editor.pageUp`, `tui.editor.pageDown`, `tui.select.pageUp`, and `tui.select.pageDown`

#### Scenario: Function keys are parsed
- **WHEN** a terminal sends common CSI or SS3 encodings for F1 through F12
- **THEN** the runtime emits `TerminalKey.Function` with the correct one-based function-key index and supported modifiers

#### Scenario: SS3 cursor keys match CSI cursor keys
- **WHEN** a terminal sends an SS3 encoding for an arrow or navigation key that also has a supported CSI encoding
- **THEN** both encodings produce the same typed key identity and modifier semantics

#### Scenario: Legacy modifier variants preserve modifiers
- **WHEN** a supported terminal sends a legacy shift, Alt, or Control variant for a cursor, navigation, or function key
- **THEN** the parser emits the corresponding typed key with the represented modifier set

#### Scenario: Functional keypad encodings are normalized
- **WHEN** a supported keyboard protocol reports a keypad code with an unambiguous functional-key meaning
- **THEN** the parser emits the normalized typed key rather than an unrelated printable scalar

#### Scenario: Jump-key control events are parsed where distinguishable
- **WHEN** a terminal sends a distinguishable sequence for `Ctrl+]` or `Ctrl+Alt+]`
- **THEN** the runtime emits typed key events that can match jump-forward or jump-backward commands

#### Scenario: Ambiguous key encodings are documented
- **WHEN** a terminal encoding cannot distinguish an upstream default binding from another key or raw control byte
- **THEN** the runtime documents the limitation and tests the closest supported typed event rather than exposing backend-specific raw strings as the primary API

#### Scenario: Parser behavior is shared where possible
- **WHEN** JVM and Scala Native backends receive the same normalized byte sequence for a supported key
- **THEN** they deliver the same typed terminal input event to shared component code

#### Scenario: Unknown sequences retain a bounded fallback
- **WHEN** the parser receives a complete escape sequence outside the supported key matrix
- **THEN** it emits the existing bounded raw representation without dropping bytes or inventing a typed key identity

### Requirement: Terminal cell-size query support
Each TUI runtime SHALL support terminal cell-size query response parsing for image capability decisions and SHALL retain the resulting valid dimensions in state owned by that runtime session rather than process-global mutable state.

#### Scenario: Cell-size response is parsed for one session
- **WHEN** a terminal sends a valid cell-size response containing pixel height and pixel width for a terminal cell
- **THEN** that runtime session exposes the positive dimensions to components rendered in that session as width and height

#### Scenario: Cell-size query has safe fallback
- **WHEN** no valid cell-size response is available in a runtime session
- **THEN** image sizing in that session can continue with default cell dimensions without blocking terminal input

#### Scenario: Invalid cell-size response is ignored
- **WHEN** a terminal sends a malformed or non-positive cell-size response
- **THEN** the receiving runtime ignores the response and does not update its session cell dimensions

#### Scenario: Concurrent runtimes remain isolated
- **WHEN** two running TUI instances receive different valid cell-size responses
- **THEN** each instance and its attached components continue using only that instance's dimensions

## ADDED Requirements

### Requirement: Session-scoped terminal diagnostics
The TUI runtime SHALL provide an opt-in instance-scoped observer for structured write, redraw, resize, and lifecycle diagnostics. Diagnostic events SHALL expose bounded metadata needed to debug terminal behavior and SHALL NOT expose application text, image payloads, or raw terminal-query replies by default.

#### Scenario: Disabled diagnostics have no callbacks
- **WHEN** an application starts a TUI without a diagnostic observer
- **THEN** rendering and terminal lifecycle behavior proceeds without diagnostic callbacks or process-global logging state

#### Scenario: Observer receives ordered metadata
- **WHEN** an opted-in TUI writes a render frame or performs a resize redraw
- **THEN** its observer receives ordered structured metadata including the operation kind and bounded geometry or byte-count fields without application content

#### Scenario: Concurrent observers remain isolated
- **WHEN** two TUI instances use different diagnostic observers
- **THEN** each observer receives events only from its owning runtime

#### Scenario: Observer failure cannot prevent cleanup
- **WHEN** a diagnostic observer throws while a runtime is rendering or stopping
- **THEN** the failure is contained according to the documented observer contract and terminal restoration still completes
