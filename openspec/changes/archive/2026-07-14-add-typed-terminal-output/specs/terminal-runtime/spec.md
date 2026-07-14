## MODIFIED Requirements

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

## ADDED Requirements

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
