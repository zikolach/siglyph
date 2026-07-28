## ADDED Requirements

### Requirement: TUI exposes callback-completed append-only output
Siglyph SHALL provide a TUI-owned operation that accepts one detached component for append-only normal-screen output and completes an application callback exactly once with a typed result.

#### Scenario: Text component is appended
- **WHEN** a caller submits a detached text-only component during an append-compatible lifecycle
- **THEN** the TUI SHALL publish its sanitized lines above the retained live frame and complete the callback with the published row and control counts

#### Scenario: Empty component is appended
- **WHEN** a valid appended component returns no lines, controls, or cursor placements
- **THEN** the TUI SHALL publish no terminal bytes and complete successfully with zero rows and controls

#### Scenario: Completion occurs before method return
- **WHEN** synchronous lifecycle rejection or uncontended draining completes the operation immediately
- **THEN** the callback MAY run before the append method returns and SHALL still run exactly once

#### Scenario: Flush is uncontended
- **WHEN** no runtime owner is active after append publication and a caller invokes `flushRender()`
- **THEN** existing synchronous uncontended draining SHALL remain unchanged

#### Scenario: Flush is contended or reentrant
- **WHEN** another runtime owner is active or append is requested from a runtime callback
- **THEN** `flushRender()` SHALL remain non-waiting and the append callback SHALL be the authoritative operation-completion boundary

### Requirement: Append admission preserves normal-screen scrollback policy
Siglyph SHALL admit append-only output only while a normal-screen TUI with scrollback-preserving resize policy is running and has a committed retained frame.

#### Scenario: Running compatible TUI accepts append
- **WHEN** `TUIScreenMode.Normal`, `NormalResizeClearPolicy.PreserveScrollback`, `Running`, and a committed retained frame all apply
- **THEN** the append SHALL enter the runtime FIFO

#### Scenario: Default resize policy is incompatible
- **WHEN** append is requested while `NormalResizeClearPolicy.ClearScrollback` is configured
- **THEN** the TUI SHALL reject it explicitly because a later resize may clear appended history and SHALL emit no append bytes

#### Scenario: Alternate-screen append is requested
- **WHEN** append is requested from a TUI using alternate-screen mode
- **THEN** the TUI SHALL reject it explicitly and SHALL emit no append bytes

#### Scenario: Append is requested before startup or initial frame
- **WHEN** append is requested before `Running` or before the first retained frame is committed
- **THEN** the TUI SHALL reject it explicitly and SHALL emit no append bytes

#### Scenario: Append is requested during or after shutdown
- **WHEN** append is requested in `Stopping`, `Cleaning`, or `Stopped`
- **THEN** the TUI SHALL reject it explicitly and SHALL emit no append bytes

#### Scenario: Stop wins before append claim
- **WHEN** stop begins after append admission but before that append is claimed
- **THEN** the append SHALL publish nothing, complete once with a stopping rejection in accepted order, and not postpone terminal restoration indefinitely

### Requirement: Append rendering is detached, restricted, and geometry-safe
Siglyph SHALL render append-only components outside retained component ownership using current session dimensions and a restricted one-shot context.

#### Scenario: Detached contextual component is appended
- **WHEN** a detached contextual component is claimed
- **THEN** it SHALL receive a restricted context exposing the owning session's current image cell dimensions and SHALL be detached in `finally` on every outcome

#### Scenario: Component capabilities remain caller-configured
- **WHEN** an appended `Image` or another component uses `TerminalCapabilities`
- **THEN** it SHALL use the capabilities configured on that component while the TUI supplies only session-owned context such as image cell dimensions

#### Scenario: Attached component is submitted
- **WHEN** the submitted component is detectable as a retained child, retained descendant, or overlay component of the same TUI
- **THEN** append SHALL reject it before changing context or publishing output

#### Scenario: One-shot component requests retained authority
- **WHEN** context attachment or rendering tries to change focus, overlays, exit state, nested flush state, or retained render scheduling
- **THEN** the restricted context SHALL fail the append before publication rather than granting lasting UI ownership

#### Scenario: Resize invalidates an unpublished candidate
- **WHEN** resize generation or terminal dimensions change between append render and commit
- **THEN** the TUI SHALL discard that candidate, keep the append ahead of later appends, and retry without publishing stale bytes

#### Scenario: Resize causes multiple render attempts
- **WHEN** one or more unpublished candidates are invalidated by resize
- **THEN** the component MAY render more than once, exactly one candidate SHALL be published, and the completion callback SHALL run exactly once

### Requirement: Append-only output preserves typed terminal authority
Siglyph SHALL validate and encode append-only `ComponentRender` controls only through the existing private TUI-owned terminal output boundary.

#### Scenario: Typed image control is appended
- **WHEN** an appended component returns a valid Kitty or iTerm2 image control with matching reserved geometry
- **THEN** the TUI SHALL encode it at its validated placement without exposing raw protocol bytes to the caller

#### Scenario: Ordinary text resembles a terminal protocol
- **WHEN** appended lines contain image, cursor, CSI, OSC, APC, DCS, C0, DEL, or C1-looking data
- **THEN** existing trusted-output sanitization SHALL apply and no typed authority SHALL be inferred from those strings

#### Scenario: Render geometry is invalid
- **WHEN** an appended render contains an out-of-bounds control, duplicate input Kitty ID, or another `ComponentRender` validation error
- **THEN** the TUI SHALL publish none of that append operation's lines or controls and SHALL enter normal runtime failure handling

#### Scenario: Appended render contains cursor placement
- **WHEN** an appended render contains one or more structured cursor placements
- **THEN** the TUI SHALL fail before publication rather than dropping metadata or transferring hardware-cursor ownership

#### Scenario: Appended render contains Kitty cleanup
- **WHEN** an appended render contains a Kitty cleanup control
- **THEN** the TUI SHALL fail before publication because destructive cleanup is not append-only output

#### Scenario: Public raw encoder remains unavailable
- **WHEN** append-only typed output is supported
- **THEN** Siglyph SHALL NOT make its raw control encoder, arbitrary trusted escape writer, or unrestricted control constructor public

### Requirement: Append operations preserve the retained live frame
Siglyph SHALL serialize append publication and physical live-frame relocation as one runtime-owned synchronized write while retaining the frame's semantic ownership.

#### Scenario: Append publishes above live frame
- **WHEN** a valid append is committed
- **THEN** the TUI SHALL clear only the replaceable live-frame region, publish complete append rows, reserve their height, and redraw one retained live frame below them

#### Scenario: Retained state survives relocation
- **WHEN** append output relocates the live frame
- **THEN** retained children, overlays, layout trees, focus, input target, typed controls, and semantic `previousFrame` content SHALL remain unchanged

#### Scenario: Hardware cursor is restored
- **WHEN** the retained frame has a selected structured cursor
- **THEN** append commit SHALL restore that cursor relative to the relocated live frame and update the runtime's logical cursor row

#### Scenario: Mouse frame origin is restored
- **WHEN** mouse frame-origin tracking is active and append output scrolls or relocates the live frame
- **THEN** the TUI SHALL update the visible retained-frame origin so coordinate-aware routing continues to target the same retained layout

#### Scenario: Append races retained work
- **WHEN** append, input callback, structural mutation, overlay action, terminal query/control, retained render, or resize work are concurrently ready
- **THEN** the existing single runtime owner SHALL order complete work units without recursive drain, second terminal lock, or interleaved append bytes

#### Scenario: Multiple appends are accepted
- **WHEN** multiple append operations are admitted concurrently
- **THEN** each SHALL remain FIFO relative to other appends and publish as one complete operation before the next append

### Requirement: Append-only Kitty identities are remapped and bounded
Siglyph SHALL isolate successful append-only Kitty controls from retained cleanup by remapping their image IDs and retaining a bounded structural ownership ledger.

#### Scenario: Kitty image is appended
- **WHEN** a valid append contains a Kitty image control
- **THEN** the TUI SHALL replace its semantic image ID with a fresh runtime-owned ID before encoding while preserving payload and geometry

#### Scenario: Same component is later rendered elsewhere
- **WHEN** a component or its original Kitty ID is reused after successful append
- **THEN** that original ID SHALL NOT identify or delete the remapped append-only placement

#### Scenario: Later retained frame uses an append-owned ID
- **WHEN** a caller-configured retained Kitty control collides with an append-owned remapped ID
- **THEN** retained-frame validation SHALL fail before terminal output rather than replacing or deleting append-only output

#### Scenario: Append identity ledger reaches capacity
- **WHEN** an append would increase append-owned Kitty IDs beyond 4096 in one TUI lifecycle
- **THEN** the append SHALL fail before publication while text-only and iTerm2 append capacity remain unaffected

#### Scenario: Ownership ledger remains redacted and structural
- **WHEN** append-owned Kitty IDs are retained
- **THEN** the ledger SHALL retain no component, payload, filename, application text, placement, geometry, or encoded output

#### Scenario: Live frame changes after Kitty append
- **WHEN** the retained frame later renders, resizes, removes a child, or stops
- **THEN** replacement, retransmission, and shutdown cleanup SHALL NOT target append-owned IDs

#### Scenario: Retained Kitty control remains cleanup-owned
- **WHEN** a Kitty image belongs to an ordinary retained component and does not collide with the append ledger
- **THEN** existing retained replacement and shutdown cleanup behavior SHALL remain unchanged

### Requirement: Append failures and diagnostics are explicit and redaction-safe
Siglyph SHALL complete append outcomes exactly once and keep diagnostic observations bounded and free of application content.

#### Scenario: Planning fails before publication
- **WHEN** rendering, restricted-context use, detachment, validation, identity planning, or output planning fails before the terminal write
- **THEN** no append bytes SHALL be published, the callback SHALL complete with failure, and normal fail-fast runtime cleanup SHALL begin

#### Scenario: Terminal write fails
- **WHEN** the backend throws after append publication has begun
- **THEN** the callback SHALL report failure, normal terminal restoration SHALL run, and Siglyph SHALL NOT report success or rollback of bytes already accepted by the backend

#### Scenario: Append diagnostics are observed
- **WHEN** a configured diagnostic observer receives append lifecycle events
- **THEN** events SHALL contain only bounded outcome/failure category, row count, control count, screen mode, and resize generation

#### Scenario: Diagnostic content is redacted
- **WHEN** append diagnostics represent success, rejection, or failure
- **THEN** they SHALL NOT retain component lines, exception messages, payloads, filenames, control bytes, remapped image IDs, or terminal write contents

#### Scenario: Completion callback throws
- **WHEN** an append completion callback throws
- **THEN** existing application-callback failure handling SHALL record the failure and still complete terminal restoration without invoking that callback twice

### Requirement: Append-only output is portable across supported runtimes
Siglyph SHALL implement append admission, planning, validation, identity ownership, serialization, and completion in shared core for JVM and Scala Native.

#### Scenario: JVM backend appends typed output
- **WHEN** a supported JVM terminal backend executes append-only work
- **THEN** it SHALL satisfy the shared ordering, validation, row reservation, result, typed-control, frame-restoration, and cleanup contracts

#### Scenario: Scala Native backend appends typed output
- **WHEN** a supported Scala Native backend executes append-only work
- **THEN** it SHALL satisfy the same semantic contracts without a separate platform implementation

#### Scenario: Automated PTY validation runs
- **WHEN** JVM PTY tests exercise append output
- **THEN** they SHALL verify emitted byte ordering, forbidden-cleanup absence, and terminal lifecycle restoration without claiming to emulate Kitty or iTerm2 scrollback

#### Scenario: Emulator persistence is checked
- **WHEN** release validation claims real Kitty or iTerm2 scrollback persistence
- **THEN** that claim SHALL come from documented manual smoke coverage in those terminal emulators
