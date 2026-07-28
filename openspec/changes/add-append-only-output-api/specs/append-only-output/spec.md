## ADDED Requirements

### Requirement: TUI appends typed components to normal-screen scrollback
Siglyph SHALL provide a TUI-owned operation that renders one component as append-only normal-screen output above the retained live frame.

#### Scenario: Text component is appended
- **WHEN** a caller appends a text-only component while a normal-screen TUI is running
- **THEN** the TUI SHALL render the component once at the current terminal width and append its sanitized lines above the retained live frame

#### Scenario: Contextual component is appended
- **WHEN** an appended component requires `TUIContext` or its terminal-relative render origin
- **THEN** the TUI SHALL supply the owning session's current context, image cell dimensions, and insertion origin while producing the one-shot render

#### Scenario: Appended rows are reserved
- **WHEN** a valid appended component produces one or more rows
- **THEN** the TUI SHALL reserve those rows in normal-screen output before restoring the retained live frame so later live rendering does not overwrite the appended content

#### Scenario: Append work is flushed
- **WHEN** a caller accepts append work and then invokes the TUI's existing render-flush completion boundary outside a runtime callback
- **THEN** that boundary SHALL not complete until the accepted append and required live-frame restoration have completed or the runtime has failed

### Requirement: Append-only output preserves typed terminal authority
Siglyph SHALL validate and encode append-only `ComponentRender` controls only through the existing TUI-owned terminal output boundary.

#### Scenario: Typed image control is appended
- **WHEN** an appended component returns a valid Kitty or iTerm2 terminal control and matching reserved geometry
- **THEN** the TUI SHALL encode that typed control at the component's validated placement without requiring or exposing raw protocol bytes to the caller

#### Scenario: Ordinary text resembles a terminal protocol
- **WHEN** appended ordinary lines contain bytes resembling an image, cursor, CSI, OSC, APC, DCS, C0, DEL, or C1 protocol
- **THEN** the TUI SHALL apply the existing trusted-output sanitization rules and SHALL NOT infer typed terminal authority from the text

#### Scenario: Render geometry is invalid
- **WHEN** an appended render contains a control outside its rows or width, a duplicate active Kitty image id, or another `ComponentRender` validation failure
- **THEN** the TUI SHALL publish none of that append operation's lines or controls and SHALL fail through bounded runtime diagnostics

#### Scenario: Appended render contains a cursor placement
- **WHEN** an appended render contains one or more structured cursor placements
- **THEN** the TUI SHALL reject the append before publication rather than dropping cursor metadata or transferring hardware-cursor ownership away from the retained live frame

#### Scenario: Public raw encoder remains unavailable
- **WHEN** append-only typed output is supported
- **THEN** Siglyph SHALL NOT make its raw `TerminalRenderControl` encoder or an arbitrary trusted escape-string writer public as part of this capability

### Requirement: Append operations are serialized with TUI runtime work
Siglyph SHALL serialize append-only output, active-frame restoration, and terminal writes with existing input, structural, query, control, render, resize, and cleanup work.

#### Scenario: Append races a retained-frame render
- **WHEN** append work and a retained-frame render are requested concurrently
- **THEN** the TUI runtime owner SHALL order both operations and SHALL produce complete appended output followed by one valid retained live frame

#### Scenario: Append races terminal resize
- **WHEN** terminal dimensions change while append work is pending
- **THEN** the runtime owner SHALL order resize and append work, render the appended component against one claimed current geometry, and leave one live frame using the final processed geometry

#### Scenario: Append is requested from a runtime callback
- **WHEN** input or another application callback requests append-only output while the TUI work drain is active
- **THEN** the operation SHALL enqueue follow-up work without recursive rendering, callback deadlock, a second terminal lock, or terminal writes outside runtime ownership

#### Scenario: Multiple append requests are accepted
- **WHEN** multiple append operations are accepted concurrently
- **THEN** the TUI SHALL publish each complete append in accepted runtime order without interleaving their lines or controls

#### Scenario: Terminal write fails
- **WHEN** the backend fails after an append publication has begun
- **THEN** the TUI SHALL follow existing runtime failure and terminal lifecycle cleanup semantics and SHALL NOT report successful or rolled-back append output

### Requirement: Append-only controls leave retained-frame cleanup ownership
Siglyph SHALL treat successfully appended terminal controls as one-shot normal-screen output that is not owned by later retained-frame replacement or TUI shutdown cleanup.

#### Scenario: Live frame changes after image append
- **WHEN** a Kitty image control has been appended successfully and the retained live frame later changes
- **THEN** retained-frame replacement SHALL NOT emit cleanup for the appended image id

#### Scenario: Terminal resizes after image append
- **WHEN** a resize causes the retained live frame to redraw after typed output was appended
- **THEN** the TUI SHALL preserve append-only control ownership semantics and SHALL NOT retransmit, move, or delete the appended control as retained-frame content

#### Scenario: TUI stops after image append
- **WHEN** a normal-screen TUI stops after successfully appending a typed control
- **THEN** shutdown SHALL restore owned terminal lifecycle state without emitting retained-frame cleanup for the append-only control

#### Scenario: Retained image remains cleanup-owned
- **WHEN** an image control belongs to an ordinary retained component rather than append-only output
- **THEN** existing replacement and shutdown cleanup behavior SHALL remain unchanged for that retained control

#### Scenario: Append planning fails
- **WHEN** append rendering or validation fails before publication
- **THEN** no append-only control identity SHALL be transferred and the retained frame SHALL remain the only cleanup-owned output

### Requirement: Append-only output is lifecycle- and mode-bounded
Siglyph SHALL accept append-only component output only during a running normal-screen TUI lifecycle.

#### Scenario: Alternate-screen append is requested
- **WHEN** append-only output is requested from a TUI using alternate-screen mode
- **THEN** the TUI SHALL reject the request explicitly and SHALL emit no append output

#### Scenario: Append is requested before startup
- **WHEN** append-only output is requested before the TUI enters its running lifecycle
- **THEN** the TUI SHALL reject the request explicitly and SHALL emit no append output

#### Scenario: Append races shutdown
- **WHEN** shutdown has begun before pending append work is claimed
- **THEN** the TUI SHALL reject the append, publish none of its output, and complete normal terminal restoration

#### Scenario: Append diagnostics are observed
- **WHEN** a configured diagnostic observer receives append lifecycle events
- **THEN** events SHALL contain only bounded structural metadata such as outcome, row count, control count, screen mode, and failure category and SHALL NOT retain application lines, payloads, filenames, protocol bytes, or terminal write contents

### Requirement: Append-only output is portable across supported runtimes
Siglyph SHALL implement append planning, typed-control validation, ownership transfer, and work serialization in shared core for JVM and Scala Native terminal backends.

#### Scenario: JVM backend appends typed output
- **WHEN** a supported JVM terminal backend executes append-only component work
- **THEN** it SHALL satisfy the shared ordering, validation, row reservation, typed-control, and cleanup contracts

#### Scenario: Scala Native backend appends typed output
- **WHEN** a supported Scala Native terminal backend executes append-only component work
- **THEN** it SHALL satisfy the same shared ordering, validation, row reservation, typed-control, and cleanup contracts without a separate semantic implementation
