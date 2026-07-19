# terminal-runtime Specification

## Purpose
Defines the terminal backend abstraction, JVM and Scala Native runtime behavior, input parsing, resize handling, protocol support, and virtual terminal test backend expectations.
## Requirements
### Requirement: Terminal abstraction
The library SHALL define terminal lifecycle, input, resize, dimensions, output, cursor, clear, and optional capability operations. `Terminal.start` SHALL return without synchronously invoking either registered callback on its calling stack. Output-side methods SHALL also remain callback-separated.

#### Scenario: Backend publishes during startup independently
- **WHEN** a backend thread observes input or resize before `start` returns
- **THEN** it may publish from that independent thread and the runtime accepts the event during `Starting`

#### Scenario: Backend does not publish on start stack
- **WHEN** `start` is called
- **THEN** neither registered callback is invoked synchronously on that call stack

#### Scenario: Backend restores terminal state
- **WHEN** lifecycle reaches `Stopped` or `run()` returns
- **THEN** terminal mode and cursor restoration have completed exactly once

### Requirement: Scala Native POSIX backend
The initial Native backend SHALL target Scala Native on macOS and Linux and use native POSIX terminal APIs for raw mode, window-size queries, signal handling, and non-buffered input.

#### Scenario: Raw mode receives unbuffered input
- **WHEN** the Scala Native backend is running in raw mode
- **THEN** a single key press is delivered without waiting for a newline

#### Scenario: Resize updates dimensions
- **WHEN** the terminal window is resized
- **THEN** the backend updates columns and rows and requests a render

### Requirement: JVM stty backend
The initial JVM backend SHALL target macOS and Linux terminals using an `stty`-based compatibility layer for raw mode and terminal restoration when attached to an interactive TTY.

#### Scenario: JVM backend enters raw mode
- **WHEN** the JVM backend starts on a Unix-like interactive TTY with `stty` available
- **THEN** it configures the terminal for unbuffered interactive input

#### Scenario: JVM backend fails clearly without stty
- **WHEN** raw-mode JVM startup is requested and `stty` is unavailable
- **THEN** the backend fails with a clear diagnostic instead of leaving the terminal in an unknown state

#### Scenario: JVM backend restores stty state
- **WHEN** the JVM backend stops after configuring raw mode
- **THEN** it restores the previously captured terminal settings

### Requirement: Non-interactive stream operation
The terminal runtime SHALL support non-interactive operation where practical by allowing stream-backed input and output without requiring raw terminal mode.

#### Scenario: Non-interactive output renders frames
- **WHEN** a stream-backed terminal is created with a non-TTY output stream
- **THEN** the renderer can write frames to that stream without invoking `stty`

#### Scenario: Non-interactive dimensions use fallback
- **WHEN** terminal dimensions cannot be queried from an interactive TTY
- **THEN** the backend uses configured dimensions or environment fallbacks such as `COLUMNS` and `LINES`

### Requirement: Shared backend compatibility
The library SHALL keep the terminal backend interface independent from target-specific implementation details so Native and JVM backends use the same component and renderer APIs.

#### Scenario: Shared core works across backends
- **WHEN** the same component tree is rendered through a Native backend and a JVM backend
- **THEN** the component rendering API and output semantics remain the same

### Requirement: Terminal protocol support
The terminal runtime SHALL support bounded bracketed-paste input, synchronized output, xterm-compatible modified keys, Kitty keyboard protocol negotiation where available, OSC hyperlinks, and typed Kitty/iTerm2 image controls behind shared terminal abstractions.

#### Scenario: Bracketed paste event is preserved
- **WHEN** a terminal sends bracketed paste start and end markers around pasted content
- **THEN** the runtime emits `PasteStart`, zero or more bounded `PasteChunk` events that preserve the content exactly once, and `PasteEnd` without retaining the complete paste in transport

#### Scenario: Synchronized output wraps frame writes
- **WHEN** the renderer flushes a frame
- **THEN** it wraps the write with terminal synchronized output enable and disable sequences when supported

#### Scenario: Kitty keyboard sequence is normalized
- **WHEN** the terminal sends a Kitty CSI-u key sequence with modifiers
- **THEN** the runtime normalizes it to the library key event model

#### Scenario: Typed image controls use the semantic output path
- **WHEN** a validated Kitty or iTerm2 image control is present in a component frame
- **THEN** the runtime preserves it as typed semantic data and encodes it only at the final synchronized output boundary

### Requirement: Virtual terminal test backend
The library SHALL provide a virtual terminal backend for tests that records writes, simulates dimensions, accepts scripted input, and exposes viewport state for assertions.

#### Scenario: Virtual terminal records frame output
- **WHEN** a test renders a TUI frame through the virtual backend
- **THEN** the backend exposes the written escape stream and rendered viewport for assertions

### Requirement: Buffered terminal input
The terminal runtime SHALL buffer incomplete raw input framing and emit bounded ordered typed input events without retaining complete paste or raw streams.

#### Scenario: Split escape sequence
- **WHEN** a terminal backend receives an arrow-key escape sequence split across multiple read chunks
- **THEN** the runtime emits one typed arrow-key event after the complete sequence is available

#### Scenario: Split bracketed paste
- **WHEN** bracketed paste start, content, and end markers arrive across multiple chunks
- **THEN** the runtime emits `PasteStart`, zero or more bounded `PasteChunk` events containing every content byte exactly once, and `PasteEnd`

#### Scenario: Incomplete escape timeout
- **WHEN** an escape sequence remains incomplete beyond the configured buffer timeout
- **THEN** the runtime flushes the incomplete data as bounded raw or best-effort input without blocking future input forever

### Requirement: Control-key normalization
The terminal input parser SHALL normalize supported raw ASCII control bytes into typed key events with control modifiers.

#### Scenario: Ctrl O raw control byte parses to typed key
- **WHEN** the input parser receives raw byte `0x0f`
- **THEN** it emits `TerminalKey.Character("o")` with Ctrl modifier state

### Requirement: Terminal protocol lifecycle
Interactive terminal backends SHALL enable terminal protocols needed for interactivity on start and disable them on stop.

#### Scenario: Bracketed paste enabled on start
- **WHEN** an interactive JVM or Native terminal backend starts
- **THEN** it enables bracketed paste mode before delivering input to the TUI

#### Scenario: Bracketed paste disabled on stop
- **WHEN** an interactive JVM or Native terminal backend stops
- **THEN** it disables bracketed paste mode before returning control to the shell

### Requirement: Safe interactive shutdown
The TUI SHALL use single-owner cleanup and SHALL discard queued ordinary work after stop while retaining accepted control output and required query completion callbacks.

#### Scenario: Uncontended stop restores synchronously
- **WHEN** no startup, drain, or query reservation owner is active
- **THEN** stop invokes retained completions and restores terminal state before returning

#### Scenario: Deferred stop preserves only retained work
- **WHEN** stop occurs while another owner or query reservation is active
- **THEN** later ordinary work is rejected, queued ordinary work is discarded, and the owner invokes retained query callbacks and accepted title/progress output before cleanup

### Requirement: Opt-in alternate-screen lifecycle
The TUI runtime SHALL support an opt-in alternate-screen mode that enters the terminal alternate screen for the lifetime of a running `TUI` instance while preserving normal-screen mode as the default.

#### Scenario: Default mode does not enter alternate screen
- **WHEN** an application constructs and starts a `TUI` without configuring alternate-screen mode
- **THEN** the terminal output does not contain `ESC[?1049h` or `ESC[?1049l`

#### Scenario: Alternate-screen mode enters before first frame
- **WHEN** an application starts a `TUI` configured for alternate-screen mode
- **THEN** the runtime emits `ESC[?1049h` before writing the first rendered frame

#### Scenario: Alternate-screen mode exits on stop
- **WHEN** an application stops a `TUI` that entered alternate-screen mode
- **THEN** the runtime emits `ESC[?1049l` before returning control to the shell

#### Scenario: Alternate-screen mode skips normal-screen cursor parking
- **WHEN** an application stops a `TUI` that entered alternate-screen mode after rendering content
- **THEN** the runtime exits alternate screen without writing the normal-screen cursor-below-content parking sequence

#### Scenario: Alternate-screen cleanup runs after runtime failure
- **WHEN** a runtime failure occurs after alternate-screen mode has been entered
- **THEN** the runtime restores autowrap if needed, shows the cursor, exits alternate screen, and stops the terminal backend

#### Scenario: Alternate-screen lifecycle is idempotent
- **WHEN** `stop()` is called more than once after an alternate-screen `TUI` has been started
- **THEN** alternate-screen exit is emitted at most once for that lifecycle

### Requirement: JVM and Native interactive parity
The project SHALL provide interactive runtime behavior and demo launch targets for both JVM and Scala Native backends.

#### Scenario: JVM interactive demo launches
- **WHEN** the JVM interactive demo is launched in a macOS or Linux TTY
- **THEN** it enters raw mode, renders the demo UI, accepts live input, and exits safely on Escape or Ctrl+C

#### Scenario: Native interactive demo launches
- **WHEN** the Scala Native interactive demo is built and launched in a macOS or Linux TTY
- **THEN** it enters raw mode, renders the demo UI, accepts live input, and exits safely on Escape or Ctrl+C

### Requirement: Positive render dimensions
The TUI runtime SHALL clamp terminal render dimensions to positive minimums before rendering.

#### Scenario: Zero width is clamped
- **WHEN** a terminal backend reports zero columns
- **THEN** the TUI renders using at least one column instead of passing zero width to components

#### Scenario: Zero height is clamped
- **WHEN** a terminal backend reports zero rows
- **THEN** the TUI tracks at least one row for redraw and viewport decisions

### Requirement: Height-aware resize redraws
The TUI runtime SHALL track both terminal width and terminal height changes across renders and SHALL repaint after dimension changes according to the active screen mode.

#### Scenario: Normal-screen width resize redraws with full clear
- **WHEN** terminal width changes after a previous render in normal-screen mode
- **THEN** the TUI emits synchronized output with autowrap disabled, clears the viewport and scrollback with `CSI 2 J`, `CSI H`, and `CSI 3 J`, and writes the recomputed frame without entering alternate screen

#### Scenario: Normal-screen height resize redraws with full clear
- **WHEN** terminal height changes after a previous render in normal-screen mode
- **THEN** the TUI emits synchronized output with autowrap disabled, clears the viewport and scrollback with `CSI 2 J`, `CSI H`, and `CSI 3 J`, and writes the recomputed frame without entering alternate screen

#### Scenario: Resize with overlay recomputes layout
- **WHEN** terminal dimensions change while an autocomplete overlay is visible in normal-screen mode
- **THEN** the overlay is re-resolved and composited into the full-clear resize redraw without entering alternate-screen mode

#### Scenario: Alternate-screen resize redraw clears active viewport
- **WHEN** terminal dimensions change after a previous render while alternate-screen mode is active
- **THEN** the TUI emits synchronized output with autowrap disabled, clears the active alternate-screen viewport, homes the cursor, and writes the recomputed frame without emitting another alternate-screen enter sequence or `CSI 3 J`

### Requirement: Runtime sanitizes over-wide output
The TUI runtime SHALL sanitize final rendered non-image lines before writing to the terminal so normal interactive rendering does not throw because a line exceeds terminal width.

#### Scenario: Over-wide line is truncated before write
- **WHEN** a component returns a rendered line whose visible width exceeds the current terminal width
- **THEN** the TUI writes an ANSI-safe truncated line whose visible width is less than or equal to the terminal width

#### Scenario: Over-wide line does not crash input handling
- **WHEN** a focused component input event causes an over-wide rendered line
- **THEN** input handling completes without an uncaught exception on the backend input thread

#### Scenario: Sanitization remains diagnosable
- **WHEN** the TUI sanitizes an over-wide line
- **THEN** tests or debug tooling can observe that sanitization occurred without adding runtime dependencies

### Requirement: JVM live resize notifications
The JVM interactive terminal backend SHALL notify the TUI when terminal dimensions change during an interactive session.

#### Scenario: JVM backend detects changed stty size
- **WHEN** the JVM backend observes a different terminal size while running
- **THEN** it updates reported columns and rows and invokes the resize callback

#### Scenario: JVM resize polling stops
- **WHEN** the JVM backend stops
- **THEN** any resize polling resources are stopped idempotently

### Requirement: Native live resize notifications
The Scala Native interactive terminal backend SHALL notify the TUI when terminal dimensions change during an interactive session.

#### Scenario: Native backend detects changed ioctl size
- **WHEN** the Native backend observes a different terminal size while running
- **THEN** it updates reported columns and rows and invokes the resize callback

#### Scenario: Native resize polling stops
- **WHEN** the Native backend stops
- **THEN** any resize polling resources are stopped idempotently

### Requirement: Resize rendering preserves terminal state
Interactive resize rendering SHALL avoid leaving the terminal in a broken state if rendering encounters recoverable width issues.

#### Scenario: Narrow resize remains interactive
- **WHEN** an interactive terminal is resized to a narrow positive width
- **THEN** the TUI redraws valid terminal output and remains able to handle later input

#### Scenario: Restore still happens on unrecoverable failure
- **WHEN** an unrecoverable rendering failure occurs during interactive input or resize
- **THEN** the TUI stops the backend and restores cursor/terminal state through the normal shutdown path

### Requirement: Overlay input routing
The TUI runtime SHALL route terminal input to the topmost visible focus-capturing overlay when one exists, otherwise to the currently focused base component.

#### Scenario: Capturing overlay receives input
- **WHEN** a visible focus-capturing overlay is topmost and terminal input is received
- **THEN** the overlay component receives the input instead of the previously focused base component

#### Scenario: Non-capturing overlay does not receive input
- **WHEN** only non-capturing overlays are visible and terminal input is received
- **THEN** input is routed to the previously focused base component

#### Scenario: Topmost capturing overlay wins
- **WHEN** multiple visible focus-capturing overlays exist
- **THEN** the topmost overlay by focus order receives terminal input

### Requirement: Overlay focus restoration
The TUI runtime SHALL restore focus predictably when overlays are hidden, removed, temporarily made invisible, or unfocused through a handle.

#### Scenario: Removing focused overlay restores previous focus
- **WHEN** the focused overlay is permanently removed
- **THEN** focus moves to the next visible capturing overlay or to the component that was focused before the removed overlay was shown

#### Scenario: Hiding focused overlay restores previous focus
- **WHEN** the focused overlay is temporarily hidden
- **THEN** focus moves to the next eligible target and the hidden overlay no longer receives input

#### Scenario: Refocusing overlay brings it to front
- **WHEN** a visible capturing overlay is focused through its handle
- **THEN** it becomes the input target and top visual overlay

### Requirement: Overlay resize redraws
The TUI runtime SHALL perform full redraws on terminal dimension changes and SHALL re-resolve, clamp, and composite overlays against the new positive render dimensions.

#### Scenario: Width resize repositions overlay
- **WHEN** terminal width changes while an overlay is visible
- **THEN** the next render recomputes the overlay width and column position before writing the frame

#### Scenario: Height resize repositions overlay
- **WHEN** terminal height changes while an overlay is visible
- **THEN** the next render recomputes the overlay maximum height and row position before writing the frame

#### Scenario: Narrow resize clips overlay safely
- **WHEN** a visible overlay is larger than the resized terminal viewport
- **THEN** the runtime clips or clamps the overlay output to positive terminal dimensions and remains interactive

### Requirement: Virtual terminal overlay testing
The virtual terminal backend SHALL support assertions for overlay rendering, overlay focus routing, and resize-safe overlay redraws.

#### Scenario: Virtual terminal records composited overlay frame
- **WHEN** a test renders base content with a visible overlay through the virtual terminal
- **THEN** the recorded viewport reflects the composited overlay cells

#### Scenario: Virtual terminal drives overlay input
- **WHEN** a test sends terminal input while a focus-capturing overlay is topmost
- **THEN** the test can observe that the overlay component handled the input

### Requirement: Image-capable runtime capability handling
The terminal runtime SHALL continue to report image capability from environment and expose enough information for image-capable components to choose rendering strategies.

#### Scenario: Capability remains deterministic
- **WHEN** terminal capability detection is executed for a known Kitty/iTerm2 terminal
- **THEN** the returned capabilities indicate the detected image protocol and true-color/hyperlink status consistently

#### Scenario: Unknown terminals do not pretend image support
- **WHEN** environment variables do not indicate an image-capable terminal
- **THEN** capabilities report no image protocol so image components render fallback text

#### Scenario: Capability reporting is testable
- **WHEN** a test injects terminal capability values
- **THEN** image-dependent code follows the injected protocol path deterministically

### Requirement: Output for protocol escapes is bounded by runtime safety expectations
The runtime SHALL keep typed image controls separate from ordinary rendered lines, sanitize ordinary lines through the established ANSI and width policy, and encode validated typed controls only within the final synchronized output boundary. Shutdown SHALL restore terminal cursor and state without requiring an image-library runtime hook.

#### Scenario: Typed image control remains outside ordinary lines
- **WHEN** a frame contains a validated typed image control
- **THEN** the runtime validates its geometry and encodes it through the semantic control channel without converting its protocol bytes to an ordinary line or applying ordinary-line sanitization to those bytes

#### Scenario: Image-like ordinary string gains no authority
- **WHEN** an ordinary rendered line contains bytes resembling a Kitty or iTerm2 image protocol
- **THEN** those bytes remain ordinary text and receive only ordinary-line sanitization without typed image behavior

#### Scenario: Output remains restorable on stop
- **WHEN** interactive runtime stops after typed protocol output may have altered cursor state
- **THEN** existing shutdown behavior restores terminal cursor and state safely without assuming any image-library runtime hook

### Requirement: Typed input coverage for keybinding parity
The terminal input model and parser SHALL support the typed key events needed by the default editor, input, and selection keybindings where those events can be recognized portably.

#### Scenario: Page keys are parsed
- **WHEN** a terminal sends common PageUp or PageDown escape sequences
- **THEN** the runtime emits typed key events that can match `tui.editor.pageUp`, `tui.editor.pageDown`, `tui.select.pageUp`, and `tui.select.pageDown`

#### Scenario: Jump-key control events are parsed where distinguishable
- **WHEN** a terminal sends a distinguishable sequence for `Ctrl+]` or `Ctrl+Alt+]`
- **THEN** the runtime emits typed key events that can match jump-forward or jump-backward commands

#### Scenario: Ambiguous key encodings are documented
- **WHEN** a terminal encoding cannot distinguish an upstream default binding from another key or raw control byte
- **THEN** the runtime documents the limitation and tests the closest supported typed event rather than exposing backend-specific raw strings as the primary API

#### Scenario: Parser behavior is shared where possible
- **WHEN** JVM and Scala Native backends receive the same normalized byte sequence for a supported default binding
- **THEN** they deliver the same typed terminal input event to shared component code

### Requirement: Kitty keyboard protocol negotiation
Interactive terminal backends SHALL support conservative Kitty keyboard protocol negotiation where available and SHALL fall back to existing key parsing when negotiation is unsupported, stale, or mismatched.

#### Scenario: Successful negotiation enables advanced key parsing
- **WHEN** a terminal responds to keyboard protocol negotiation with a valid matching response
- **THEN** the backend records that Kitty keyboard protocol is active and parses subsequent CSI-u keyboard events accordingly

#### Scenario: Stale negotiation response is ignored
- **WHEN** a delayed or mismatched keyboard protocol response is received after negotiation has timed out or changed
- **THEN** the backend ignores that response and does not falsely mark Kitty keyboard protocol active

#### Scenario: Unsupported terminal preserves basic input
- **WHEN** a terminal does not support Kitty keyboard protocol negotiation
- **THEN** basic arrows, printable characters, control keys, and bracketed paste continue to work through existing parsing paths

### Requirement: Key release and repeat input events
The terminal input model SHALL represent key press, release, and repeat metadata for protocols that report it, while preserving press-only behavior for terminals that do not.

#### Scenario: Key release is delivered to interested component
- **WHEN** a focused component opts into key-release handling and the terminal reports a key-release event
- **THEN** the TUI routes the typed key event with release metadata to that component

#### Scenario: Key release is ignored by default
- **WHEN** a focused component does not opt into key-release handling and the terminal reports a key-release event
- **THEN** the TUI does not deliver that release event as an ordinary key press

#### Scenario: Key repeat is distinguishable
- **WHEN** the terminal reports a repeated key event
- **THEN** the parsed terminal input identifies it as a repeat rather than an initial press

### Requirement: Platform modifier fallbacks
Terminal backends SHALL provide best-effort platform-specific modifier fallbacks for known terminals where practical without compromising normal raw input behavior.

#### Scenario: Apple Terminal modified Enter fallback
- **WHEN** Apple Terminal sends plain Return while platform state indicates a modified Enter combination that should insert a newline
- **THEN** the backend or parser emits a typed Enter event with the appropriate modifier when that fallback is available

#### Scenario: Fallback unavailable uses plain key
- **WHEN** platform modifier state cannot be queried safely
- **THEN** the backend emits the ordinary parsed key event and does not block input waiting for modifier metadata

### Requirement: Keyboard protocol diagnostics
Keyboard protocol support SHALL be testable and diagnosable without requiring a live terminal session.

#### Scenario: Parser tests cover protocol event metadata
- **WHEN** tests feed CSI-u sequences containing press, release, and repeat event metadata
- **THEN** the parser returns typed terminal input values that expose the expected event metadata

#### Scenario: Backend exposes protocol state for tests
- **WHEN** a test backend simulates keyboard protocol negotiation outcomes
- **THEN** tests can verify whether advanced keyboard mode is active without inspecting private backend state

### Requirement: Optional terminal title support
The terminal runtime SHALL expose optional support for setting the terminal window title without adding a required method to the base `Terminal` abstraction.

#### Scenario: Supported terminal sets title
- **WHEN** application code requests a terminal title on a backend that supports title operations
- **THEN** the backend emits the terminal title sequence for the sanitized title text

#### Scenario: Unsupported terminal reports no title support
- **WHEN** application code requests a terminal title on a backend without title support
- **THEN** the operation reports unsupported status and does not emit a title escape sequence

### Requirement: Optional terminal progress support
The terminal runtime SHALL expose optional fire-and-forget progress indicator support without adding a required method to the base `Terminal` abstraction.

#### Scenario: Supported terminal activates progress
- **WHEN** application code requests active terminal progress on a backend that supports progress operations
- **THEN** the backend emits OSC 9;4 active progress output and does not promise terminal display state

#### Scenario: Supported terminal clears progress
- **WHEN** application code requests inactive terminal progress on a backend that supports progress operations
- **THEN** the backend emits OSC 9;4 clear progress output

#### Scenario: Unsupported terminal reports no progress support
- **WHEN** application code requests terminal progress on a backend without progress support
- **THEN** the operation reports unsupported status and does not emit a progress escape sequence

### Requirement: Runtime-owned terminal background color query
The TUI runtime SHALL query terminal default background color through OSC 11 request/response handling owned by `TUI`.

#### Scenario: OSC 11 RGB response is parsed
- **WHEN** `TUI` queries terminal background color and receives a valid OSC 11 response
- **THEN** it returns red, green, and blue channel values as integers in the inclusive range 0 through 255

#### Scenario: OSC 11 query times out
- **WHEN** `TUI` queries terminal background color and no valid response arrives before the configured timeout
- **THEN** it returns an empty result and continues routing later user input normally

#### Scenario: Invalid OSC 11 response is ignored
- **WHEN** `TUI` receives an OSC 11 response with an invalid color payload
- **THEN** it ignores that payload and does not guess a background color

### Requirement: Runtime-owned terminal color-scheme query
The TUI runtime SHALL query terminal color scheme through request/response handling owned by `TUI` and SHALL represent the finite schemes as `dark` and `light`.

#### Scenario: Color-scheme report returns dark
- **WHEN** `TUI` receives a valid terminal color-scheme report for dark mode
- **THEN** it returns the `dark` color-scheme value

#### Scenario: Color-scheme report returns light
- **WHEN** `TUI` receives a valid terminal color-scheme report for light mode
- **THEN** it returns the `light` color-scheme value

#### Scenario: Color-scheme query times out
- **WHEN** `TUI` queries terminal color scheme and no valid report arrives before the configured timeout
- **THEN** it returns an empty result and keeps the TUI interactive

### Requirement: Terminal color-scheme notifications
The TUI runtime SHALL allow applications to enable or disable terminal color-scheme notifications and subscribe to parsed color-scheme changes.

#### Scenario: Notification listener receives scheme
- **WHEN** notifications are enabled and the terminal sends a valid color-scheme report
- **THEN** each subscribed listener receives the parsed `dark` or `light` value

#### Scenario: Disabled notifications do not notify listeners
- **WHEN** notifications are disabled and the terminal sends a color-scheme report
- **THEN** the runtime consumes or ignores the protocol report without notifying listeners as a user key event

#### Scenario: Listener unsubscribe stops callbacks
- **WHEN** a listener unsubscribes from color-scheme changes
- **THEN** later color-scheme reports do not invoke that listener

### Requirement: Runtime protocol reply interception
The TUI runtime SHALL intercept terminal protocol replies used by runtime services before routing input to focused components.

#### Scenario: Background response is not component input
- **WHEN** an OSC 11 background color response arrives during an interactive session
- **THEN** the runtime consumes it for query handling and does not deliver it as typed input to the focused component

#### Scenario: Color-scheme report is not component input
- **WHEN** a terminal color-scheme report arrives during an interactive session
- **THEN** the runtime consumes it for query or notification handling and does not deliver it as typed input to the focused component

#### Scenario: Unrelated input still reaches component
- **WHEN** user keyboard input arrives while a terminal query is pending
- **THEN** unrelated input is routed through normal focused-component handling

### Requirement: Terminal cell-size query support
The terminal runtime SHALL support terminal cell-size query response parsing for image capability decisions.

#### Scenario: Cell-size response is parsed
- **WHEN** the terminal sends a valid cell-size response containing pixel height and pixel width for a terminal cell
- **THEN** the runtime exposes those positive dimensions to image sizing code as width and height

#### Scenario: Cell-size query has safe fallback
- **WHEN** no valid cell-size response is available
- **THEN** image sizing can continue with default cell dimensions without blocking terminal input

#### Scenario: Invalid cell-size response is ignored
- **WHEN** the terminal sends a malformed or non-positive cell-size response
- **THEN** the runtime ignores the response and does not update image cell dimensions

### Requirement: Cell-size protocol reply interception
The TUI runtime SHALL consume terminal cell-size protocol replies before routing input to focused components.

#### Scenario: Cell-size response is not component input
- **WHEN** a terminal cell-size response arrives during an interactive session
- **THEN** the runtime consumes it for capability handling and does not deliver it as typed input to the focused component

#### Scenario: Unrelated input still routes normally
- **WHEN** keyboard input arrives while a cell-size query is pending
- **THEN** unrelated input is routed through normal focused-component handling

### Requirement: Optional terminal input drain capability
The terminal runtime SHALL expose an optional input-drain capability for backends that can safely flush pending terminal input or protocol replies before shutdown.

#### Scenario: Supported backend drains before stop completes
- **WHEN** a running TUI is stopped with a backend that supports input draining
- **THEN** the runtime invokes the drain capability before terminal state restoration completes

#### Scenario: Unsupported backend stops without drain
- **WHEN** a running TUI is stopped with a backend that does not support input draining
- **THEN** shutdown proceeds through the existing stop path without emitting unsupported drain operations

#### Scenario: Drain is bounded
- **WHEN** a backend drain operation waits for pending input to become idle
- **THEN** the operation uses a bounded timeout so shutdown cannot block indefinitely

#### Scenario: Stop remains idempotent
- **WHEN** stop is called more than once on a backend with input-drain support
- **THEN** input draining and terminal restoration remain safe and terminal state is restored at most once

### Requirement: Insert key identity
The terminal input model SHALL represent the Insert key as a first-class typed key identity.

#### Scenario: Insert escape sequence parses to typed key
- **WHEN** the input parser receives the standard Insert key escape sequence `CSI 2 ~`
- **THEN** it emits a typed key event with `TerminalKey.Insert`

#### Scenario: Modified Insert sequence preserves modifiers
- **WHEN** the input parser receives a supported modified Insert key sequence
- **THEN** it emits `TerminalKey.Insert` with the parsed modifier state

#### Scenario: Insert is not reported as unknown
- **WHEN** a supported Insert key sequence is parsed
- **THEN** the parser does not emit `TerminalKey.Unknown("insert")` for that sequence

### Requirement: Modified Enter tilde sequence parsing
The terminal input parser SHALL normalize supported tilde-form modified Enter sequences into typed Enter key events with modifier state.

#### Scenario: Shift Enter tilde sequence parses to typed key
- **WHEN** the input parser receives `ESC[13;2~`
- **THEN** it emits `TerminalKey.Enter` with Shift modifier state

#### Scenario: Alt Enter tilde sequence parses to typed key
- **WHEN** the input parser receives `ESC[13;3~`
- **THEN** it emits `TerminalKey.Enter` with Alt modifier state

#### Scenario: Existing modified Enter sequences remain typed
- **WHEN** the input parser receives `ESC[13;2u`, `ESC[27;2;13~`, `ESC[13;3u`, or `ESC[27;3;13~`
- **THEN** it emits `TerminalKey.Enter` with the parsed modifier state instead of raw input

### Requirement: Runtime ingress is ordered, lossless, and bounded
The TUI SHALL use one ordered ordinary ingress FIFO with capacity 4096 terminal events. One ordinary input SHALL consume one event, one protocol reply callback batch SHALL consume one event, and resize SHALL remain coalesced without consuming a slot.

#### Scenario: Running ingress preserves publication order
- **WHEN** ordinary input, query reply batches, and notifications are accepted while running
- **THEN** application-visible callbacks execute in accepted FIFO order

#### Scenario: Full ingress applies backpressure
- **WHILE** lifecycle is `Starting` or `Running` and 4096 events are queued
- **WHEN** another backend publisher submits an event
- **THEN** it waits in a lifecycle condition loop without holding the terminal write lock and no input is dropped

#### Scenario: Dequeue wakes publishers
- **WHEN** the drain removes an ingress event and frees capacity
- **THEN** it notifies blocked publishers so one can enqueue and preserve order

#### Scenario: Stop wakes and rejects publishers
- **WHEN** lifecycle changes to `Stopping` or `Stopped`
- **THEN** all blocked publishers wake, the ordinary ingress queue is cleared, and blocked and later publication is rejected

#### Scenario: Failure wakes publishers
- **WHEN** runtime or startup failure clears ordinary ingress
- **THEN** blocked publishers wake and observe lifecycle rejection

### Requirement: Callback query flights are correlated without application ingress code
The runtime SHALL maintain one reserved or emitted flight per query protocol, reserve direct request output for the first subscriber, and queue completion batches without invoking application code on ingress.

#### Scenario: First subscriber reserves and emits once
- **WHEN** a query subscribes while running with no flight
- **THEN** it reserves one flight and one write reservation, emits exactly one request through the terminal write boundary, releases the reservation, and returns cancellation

#### Scenario: Existing flight accepts subscriber
- **WHILE** a reserved or emitted flight exists
- **WHEN** another caller subscribes
- **THEN** it joins that flight and no request is emitted

#### Scenario: Reply correlation queues callbacks
- **WHEN** a valid or recognized strict invalid reply matches a flight
- **THEN** ingress clears the flight under short lifecycle state and queues one ordered callback batch without invoking application code

#### Scenario: Unrelated input remains ordinary
- **WHEN** input does not match a recognized protocol frame
- **THEN** it remains normally ordered ordinary ingress

### Requirement: Query callbacks are drain-owned and race-defined
Each completion SHALL be claimed under short lifecycle state and invoked outside lifecycle and terminal-write locks. Application callbacks SHALL never run concurrently.

#### Scenario: Cancellation removes active subscriber
- **WHEN** cancellation wins before callback claim
- **THEN** the callback is never invoked

#### Scenario: Claim prevents cancellation removal
- **WHEN** callback claim wins first
- **THEN** callback runs exactly once and cancellation has no effect

#### Scenario: Callback failure continues batch
- **WHEN** one completion callback throws
- **THEN** runtime failure is recorded, every remaining eligible callback is attempted, and cleanup proceeds

### Requirement: Query stop and failure ordering
Cleanup SHALL not overtake query write reservations or accepted retained query completions.

#### Scenario: Emitted flight stops
- **WHEN** stop observes an emitted query flight
- **THEN** active subscribers receive retained `Stopped` before cleanup

#### Scenario: Reserved flight emission succeeds after stop
- **WHEN** a reserved request emits successfully after lifecycle became stopping
- **THEN** its active subscribers receive retained `Stopped` before cleanup

#### Scenario: Reserved flight emission fails after stop
- **WHEN** reserved request emission fails
- **THEN** subscribers receive retained `Failed`, runtime failure is recorded, all completion callbacks continue, and cleanup follows reservation release

#### Scenario: Ordinary queued work remains discarded
- **WHEN** stop or failure progresses with retained query callbacks
- **THEN** the drain invokes those callbacks before cleanup but does not invoke queued ordinary input, notification, render, action, or other ordinary work

### Requirement: Runtime work remains live across application locks
The runtime SHALL serialize application callbacks, rendering, and terminal output without lifecycle/application lock inversion.

#### Scenario: Application lock and render request do not deadlock
- **WHEN** one thread holds application state and publishes render work while the owner needs that state
- **THEN** publication does not wait for the active owner and the owner continues after application state is released

### Requirement: Resize rendering rejects known stale dimensions
The runtime SHALL compare resize generation and positive dimensions immediately before frame output and differential baseline mutation.

#### Scenario: Stale candidate is rejected
- **WHEN** generation, width, or height differs from the render snapshot
- **THEN** no stale frame is committed and a forced redraw uses the latest dimensions

### Requirement: Built-in terminal callback separation
VirtualTerminal, StreamTerminal, SttyTerminal, and PosixTerminal SHALL satisfy start-stack and output-side callback separation.

#### Scenario: Portable and backend contract suites execute
- **WHEN** JVM and Scala Native terminal tests run
- **THEN** controlled callback probes show no synchronous callback from `start` or any output-side method
#### Scenario: Stop invalidates ordered input delivery
- **WHILE** a read or flush batch waits behind an earlier callback
- **WHEN** stop invalidates its terminal-start generation
- **THEN** the waiter returns without callback or counter advancement, and a later start uses reset counters and rejects old-generation delivery
#### Scenario: Stale input parsing is rejected before evaluation
- **WHILE** an old read or flush thread retains a parse operation
- **WHEN** stop and start replace its generation
- **THEN** the old parse operation is not evaluated and cannot consume or mutate the new generation's parser state
#### Scenario: Old loops cannot affect a restarted backend
- **WHILE** a read or flush loop belongs to an invalidated start generation
- **WHEN** a later generation is active
- **THEN** the old loop stops without parsing, delivering callbacks, or changing the later generation's running state

#### Scenario: Restart waits for exclusive input-reader ownership
- **WHILE** a stopped generation's input reader remains alive
- **WHEN** restart is requested
- **THEN** the backend throws `IllegalStateException` before creating another reader, and restart succeeds after the old reader terminates

#### Scenario: Interrupted ordered delivery callback fails
- **WHILE** an ordered delivery waiter has observed interruption
- **WHEN** its accepted callback throws
- **THEN** batch ordering advances and the thread's interrupted status is restored

### Requirement: Terminal input parsing is bounded byte streaming
The runtime SHALL parse `TerminalInputChunk` values without whole-stream strings, retain at most 4096 typed-candidate bytes, five paste-end prefix bytes, and three incomplete UTF-8 bytes, and emit exact ordered bounded stream events.

#### Scenario: Paste streams without accumulation
- **WHILE** paste mode is active
- **WHEN** arbitrary bytes arrive, including markers split at any boundary
- **THEN** start and end markers are omitted, every data byte appears once in chunks of at most 4096 bytes, and complete paste content is never retained
#### Scenario: Periodic flush preserves active paste
- **WHILE** paste mode is active
- **WHEN** periodic fragment flush runs with paste content or a partial end marker buffered
- **THEN** flush emits nothing and preserves paste mode and the partial marker until later bytes complete the end marker

#### Scenario: Typed protocol exceeds its bound
- **WHEN** byte 4097 arrives before an incomplete typed protocol terminates
- **THEN** retained bytes are emitted as raw chunks, later bytes remain raw without typed reclassification, and termination reports `LimitExceeded`

#### Scenario: Text decoding is incremental
- **WHEN** UTF-8 scalars cross chunk boundaries or malformed or incomplete text is flushed
- **THEN** the text decoder carries at most three bytes and emits U+FFFD where required while raw input bytes remain exact

### Requirement: Cleanup has an explicit finite Cleaning phase
The lifecycle SHALL progress `Stopped -> Starting -> Running -> Stopping -> Cleaning -> Stopped`, commit cleanup atomically after startup and query-write ownership ends, and prevent query registration from postponing restoration.

#### Scenario: Stopping query precedes cleanup commit
- **WHEN** a query registration linearizes in `Stopping` before cleanup commit
- **THEN** its `Stopped` completion belongs to the finite pre-cleanup set and runs before restoration

#### Scenario: Cleaning query follows restoration
- **WHEN** a query registers after cleanup commit and before restoration cutoff
- **THEN** its `Stopped` completion runs after restoration and cannot delay restoration

#### Scenario: Continuous registration cannot extend cleanup
- **WHEN** queries register continuously during Cleaning
- **THEN** restoration detaches one finite post-restoration list, later registrations use stopped behavior, and lifecycle reaches Stopped

### Requirement: Protocol correlation uses exact ingress slot accounting
Correlation-only raw fragments SHALL consume zero ingress slots. A recognized final protocol completion or notification batch SHALL consume exactly one slot regardless of subscriber count. Reconstructed ordinary raw events SHALL each consume one slot.

#### Scenario: Correlation fragment needs no capacity
- **WHILE** an eligible raw reply is incomplete
- **WHEN** `RawStart` or `RawChunk` produces no application work
- **THEN** correlation state advances without waiting for or consuming FIFO capacity

#### Scenario: Completion is one batch slot
- **WHEN** a recognized reply completes any number of query subscribers or notification listeners
- **THEN** its final application callback batch consumes exactly one FIFO slot

#### Scenario: Replay uses ordinary capacity
- **WHEN** a correlated stream proves unrelated or exceeds the correlation bound
- **THEN** every reconstructed raw stream event is published through normal bounded capacity in exact order, one event per slot, with bytes and required active query flight preserved

#### Scenario: Maximal replay starts draining before continuation completion
- **WHILE** replay exceeds the 4096-slot FIFO capacity and no drain is active
- **WHEN** classification atomically commits the correlation transition and bounded replay continuation
- **THEN** the publisher claims the drain before waiting, each dequeued event admits the next replay event, and publication completes without self-deadlock

#### Scenario: Later ingress cannot overtake replay continuation
- **WHILE** a bounded replay continuation remains
- **WHEN** another publisher submits ordinary ingress
- **THEN** it waits until replay admission completes or lifecycle rejection occurs, and accepted later ingress follows every replay event

#### Scenario: Stop discards replay continuation
- **WHILE** a bounded replay continuation remains
- **WHEN** stop or failure clears ordinary ingress
- **THEN** the continuation is discarded and all blocked publishers wake to observe lifecycle rejection

#### Scenario: Blocked classification is non-mutating
- **WHEN** classification needs an output slot and ingress is full
- **THEN** it waits without changing correlation, notification, or query-flight state and atomically commits the transition with the first enqueued output after capacity becomes available

### Requirement: Starting backpressure does not retain backend start
`Terminal.start` SHALL return independently of callbacks invoked from other threads. Application callbacks accepted during `Starting` SHALL remain deferred until `Running`, even when bounded Starting ingress backpressures publishers.

#### Scenario: Full Starting ingress blocks a publisher
- **WHILE** Starting ingress is full
- **WHEN** a publisher blocks
- **THEN** backend start returns independently, transitions can reach Running, and Running can drain the blocked ingress without invoking application callbacks during Starting

### Requirement: Cleaning callback sets are finite and globally serialized
Cleaning SHALL have one finite pre-restoration callback set and one finite post-restoration callback set, SHALL serialize both through the single owner, and SHALL prevent later query registration from overlapping callbacks or extending restoration.

#### Scenario: Late query registers during detached callbacks
- **WHILE** detached Cleaning callbacks execute
- **WHEN** a late query registers
- **THEN** the runtime prevents concurrent application callback execution and the registration cannot extend restoration

#### Scenario: Restoration seals post-restoration work
- **WHEN** restoration completion detaches the post-restoration callback set
- **THEN** that finite set runs serially and later registrations use stopped behavior

### Requirement: Escape Alt framing uses flush as its sequence boundary
The parser SHALL frame Escape followed by one multibyte UTF-8 scalar as one Alt input without duplicating or losing bytes when the complete scalar arrives before parser flush. Parser flush SHALL end pending non-paste Escape and Alt framing.

#### Scenario: Alt scalar crosses read fragments before flush
- **WHEN** Escape and the bytes of a multibyte UTF-8 scalar arrive in separate read fragments before parser flush
- **THEN** the parser emits exactly one Alt input for that scalar

#### Scenario: Flush follows pending Escape
- **WHEN** parser flush occurs after Escape and before another scalar byte arrives
- **THEN** flush emits standalone Escape and later bytes start a separate sequence

#### Scenario: Flush follows incomplete Alt scalar
- **WHEN** parser flush occurs after Escape and an incomplete multibyte UTF-8 scalar prefix
- **THEN** flush emits that framing with incomplete raw termination and later bytes start a separate sequence

### Requirement: Ordered delivery validates every event and restart excludes old workers
Ordered delivery SHALL recheck terminal-start generation before each event, and restart SHALL reject a live old-generation reader, flush worker, or backend resize worker.

#### Scenario: Generation changes within a batch
- **WHEN** generation becomes stale after one event in an ordered batch
- **THEN** delivery rejects every remaining event before invoking its callback

#### Scenario: Old worker remains live
- **WHILE** an old-generation reader, flush worker, or backend resize worker remains live
- **WHEN** restart is requested
- **THEN** restart throws `IllegalStateException` before starting new-generation workers

### Requirement: Finite stream EOF preserves only complete non-paste framing
At EOF, finite StreamTerminal input SHALL deliver the complete final non-paste event vector before invalidating the generation and SHALL discard incomplete paste framing without a synthetic end event.

#### Scenario: EOF flushes pending framing
- **WHEN** EOF occurs
- **THEN** the stream backend delivers the complete final non-paste event vector before generation invalidation and discards incomplete paste framing

#### Scenario: EOF interrupts paste
- **WHILE** bracketed paste has started without its end marker
- **WHEN** EOF occurs
- **THEN** the backend emits no synthetic `PasteEnd`

### Requirement: Backend cleanup obligations are independently retained
JVM and Native terminal backends SHALL retain and retry each failed cleanup obligation independently, SHALL not repeat successful obligations, and SHALL reject restart while any obligation remains.

#### Scenario: Duplicate running start takes precedence
- **WHILE** SttyTerminal is running
- **WHEN** `start` is called again
- **THEN** it throws an explicit already-running error instead of an incomplete-cleanup error

#### Scenario: One cleanup obligation fails
- **WHEN** one cleanup obligation fails after another succeeds
- **THEN** retry executes only the failed obligation and restart remains rejected until it succeeds

#### Scenario: PrintStream suppresses cleanup output failure
- **WHEN** configured PrintStream output records an error without throwing during cleanup output and replaces `System.out` while writing
- **THEN** the backend checks the same PrintStream that performed the write, throws `IOException` before clearing that cleanup obligation, and rejects restart while it remains pending

### Requirement: Missing controlling terminal is actionable
SttyTerminal SHALL report an actionable initial missing `/dev/tty` error, preserve its cause, and SHALL not fall back to another terminal source.

#### Scenario: Initial `/dev/tty` open fails
- **WHEN** SttyTerminal cannot open `/dev/tty` during initial startup
- **THEN** startup fails with an actionable diagnostic whose cause is the original error and no fallback is attempted

### Requirement: Runtime separates ordinary lines from semantic controls
The TUI runtime SHALL prepare, compare, and write ordinary rendered lines separately from typed semantic terminal controls. It SHALL NOT infer trusted controls from string prefixes or parse ordinary text into control authority.

#### Scenario: Ordinary line follows text policy
- **WHEN** a rendered ordinary line contains escape-looking image bytes
- **THEN** it remains ordinary text and is processed only by the active ordinary-line ANSI and width policy

#### Scenario: Typed control follows control policy
- **WHEN** a prepared frame contains a valid known `TerminalRenderControl`
- **THEN** the runtime validates its placement and encodes it through the exhaustive shared encoder during final buffer assembly

### Requirement: Typed control output preserves runtime ownership
Typed control encoding and output SHALL remain inside the existing serialized render owner, synchronized-output boundary, resize-generation check, terminal-write lock, and cleanup path.

#### Scenario: Resize invalidates control frame
- **WHEN** dimensions or resize generation change before a frame containing typed controls commits
- **THEN** no control from the stale frame is written and a forced redraw uses current dimensions

#### Scenario: Control write failure cleans up
- **WHEN** terminal output fails while writing a frame containing typed controls
- **THEN** the runtime records the failure and restores terminal state through the existing single-owner cleanup path
#### Scenario: Pure control reorder triggers output
- **WHEN** ordinary lines and control values are unchanged but the prepared control vector order changes
- **THEN** differential rendering selects the earliest row affected by the first ordered difference and rewrites controls in the new order

#### Scenario: Existing Kitty ID is cleaned before retransmission
- **WHEN** a Kitty `a=T` control in the rewritten range uses an ID present in the previous prepared frame
- **THEN** the runtime emits exactly one typed `a=d,d=I,i=<id>` cleanup before any replacement transmission

#### Scenario: Removed Kitty ID is cleaned without replacement
- **WHEN** an old Kitty ID is absent from the new prepared frame
- **THEN** the runtime emits exactly one typed `a=d,d=I,i=<id>` cleanup without retransmitting the old image

#### Scenario: New and out-of-range Kitty IDs are not cleaned
- **WHEN** a transmitted Kitty ID is new or an unchanged old ID is outside the rewritten row range
- **THEN** the runtime emits no lifecycle cleanup for that ID

#### Scenario: Kitty cleanup order is deterministic
- **WHEN** a partial, reorder, forced, resize, move, or replacement redraw retransmits multiple old Kitty IDs
- **THEN** the runtime emits one cleanup per ID in previous-frame control order before all replacement transmissions

#### Scenario: Direct terminal write remains explicit
- **WHEN** application code bypasses TUI and calls a terminal backend write method directly
- **THEN** that direct output is outside the component trust boundary and receives no component-render sanitization promise

### Requirement: Opt-in terminal mouse protocol lifecycle
Interactive terminal backends SHALL enable xterm normal mouse tracking and SGR mouse coordinate reporting only when mouse input is enabled through public TUI options.

#### Scenario: Mouse disabled by default
- **WHEN** an interactive JVM or Native TUI starts without mouse input enabled
- **THEN** the backend does not emit mouse reporting enable sequences

#### Scenario: Mouse enabled on start
- **WHEN** an interactive JVM or Native TUI starts with mouse input enabled
- **THEN** the backend enables normal mouse tracking with `CSI ? 1000 h` and SGR mouse coordinates with `CSI ? 1006 h`

#### Scenario: Mouse disabled on stop
- **WHEN** an interactive JVM or Native TUI with mouse input enabled stops
- **THEN** the backend disables normal mouse tracking with `CSI ? 1000 l` and SGR mouse coordinates with `CSI ? 1006 l` before returning control to the shell

#### Scenario: Startup failure restores mouse mode
- **WHEN** startup fails after mouse reporting has been enabled
- **THEN** the backend disables mouse reporting before restoring terminal state or propagating the failure

### Requirement: SGR mouse input buffering
The terminal runtime SHALL buffer fragmented SGR mouse reports and emit one logical typed input event after a complete report is available.

#### Scenario: Split SGR mouse report
- **WHEN** a mouse report such as `CSI < 64 ; 10 ; 5 M` arrives across multiple raw read chunks
- **THEN** the input buffer emits one parsed `TerminalInput.Mouse` event after the final `M` byte arrives

#### Scenario: Incomplete SGR mouse report flushes as raw input
- **WHEN** an SGR mouse report remains incomplete beyond the configured incomplete escape timeout
- **THEN** the runtime flushes the incomplete data as raw input without blocking later input

### Requirement: SGR mouse parsing
The terminal input parser SHALL parse xterm SGR mouse reports for button press, button release, and wheel input.

#### Scenario: SGR press parses
- **WHEN** the parser receives `CSI < 0 ; 3 ; 2 M`
- **THEN** it emits a left-button press at column 2 and row 1

#### Scenario: SGR release parses
- **WHEN** the parser receives `CSI < 0 ; 3 ; 2 m`
- **THEN** it emits a left-button release at column 2 and row 1

#### Scenario: SGR wheel up parses
- **WHEN** the parser receives `CSI < 64 ; 3 ; 2 M`
- **THEN** it emits a wheel-up event at column 2 and row 1

#### Scenario: SGR modifiers parse
- **WHEN** the parser receives an SGR mouse report whose modifier bits include shift, alt, and ctrl
- **THEN** it emits a mouse event whose `KeyModifiers` has `shift`, `alt`, and `ctrl` set to true and `superKey` set to false

### Requirement: Virtual terminal mouse support
The virtual terminal backend SHALL support tests that enable mouse input, feed typed or raw mouse events, and assert written protocol sequences.

#### Scenario: Virtual terminal records mouse mode sequences
- **WHEN** a TUI with mouse input enabled starts and stops on a virtual terminal
- **THEN** the test can assert that mouse enable and disable sequences were written

#### Scenario: Virtual terminal drives mouse routing
- **WHEN** a test sends a mouse event to the virtual terminal after rendering a frame
- **THEN** the test can observe which component handled the routed event

### Requirement: Mouse frame origin tracking
The terminal runtime SHALL track the visible origin of mouse-enabled TUI frames without clearing terminal scrollback on initial render.

#### Scenario: Cursor position seeds mouse frame origin
- **WHEN** a mouse-enabled interactive TUI starts
- **THEN** the runtime queries the terminal cursor position before the first frame render and uses the response to map mouse coordinates to retained frame rows

#### Scenario: Missing cursor position does not guess routing offset
- **WHEN** the terminal does not provide a usable cursor-position report before rendering
- **THEN** coordinate-aware component mouse routing is ignored until a frame origin is known

#### Scenario: Late cursor position enables mouse routing
- **WHEN** a usable cursor-position report arrives after the initial startup wait
- **THEN** the runtime records the frame origin, rerenders, and enables coordinate-aware component mouse routing

### Requirement: Mouse reporting captures terminal wheel scrollback
Interactive terminal mouse reporting SHALL document that wheel events are delivered to the application rather than normal terminal scrollback while mouse input is enabled.

#### Scenario: Unhandled wheel cannot be passed back
- **WHEN** a wheel event is delivered to the application by terminal mouse reporting and no component handles it
- **THEN** the runtime preserves focus and does not attempt to pass that event back to terminal scrollback

