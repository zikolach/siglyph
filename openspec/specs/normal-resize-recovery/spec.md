# normal-resize-recovery Specification

## Purpose
Defines opt-in, bounded, text-only recovery of application-owned normal-screen history during terminal resize.
## Requirements
### Requirement: TUI exposes opt-in normal resize recovery
Siglyph SHALL expose a shared-core `NormalResizeRecoveryProvider`, `NormalResizeRecoveryContext`, and additive `TUIOptions.normalResizeRecovery` option for reconstructing durable normal-screen viewport history during resize. The option SHALL default to absent and SHALL NOT change existing applications.

#### Scenario: Existing options have no provider
- **WHEN** an application constructs `TUIOptions` without normal resize recovery
- **THEN** normal-screen and alternate-screen rendering SHALL retain their existing resize behavior

#### Scenario: Compatible recovery is configured
- **WHEN** a provider is configured with `TUIScreenMode.Normal` and `NormalResizeClearPolicy.PreserveScrollback`
- **THEN** the TUI SHALL make that provider eligible only for committed geometry-changing resize redraws

#### Scenario: Recovery is configured with scrollback clearing
- **WHEN** a provider is configured with `NormalResizeClearPolicy.ClearScrollback`
- **THEN** TUI startup SHALL fail before backend startup or terminal output rather than silently discard recovery output or promise persistence

#### Scenario: Recovery is configured for alternate screen
- **WHEN** a provider is configured with `TUIScreenMode.Alternate`
- **THEN** TUI startup SHALL fail before backend startup or terminal output because alternate-screen output is not normal shell scrollback

#### Scenario: Initial render occurs
- **WHEN** a compatible TUI starts and commits its initial frame
- **THEN** the provider SHALL NOT be invoked and initial output SHALL retain existing first-render behavior

#### Scenario: Ordinary render work occurs
- **WHEN** an input, action, overlay, structure mutation, append, image cell-size update, ordinary differential redraw, or application-forced render occurs without terminal geometry change
- **THEN** the provider SHALL NOT be invoked

#### Scenario: Backend reports unchanged geometry
- **WHEN** a resize callback reports the same positive width and height as the committed frame
- **THEN** the provider SHALL NOT be invoked and the active viewport SHALL NOT be destructively cleared

### Requirement: Recovery uses strict previous/current live-frame-derived row bounds
Siglyph SHALL render, compose, validate, and prepare the retained live frame first. It SHALL
calculate current capacity as `max(0, terminalHeight - max(1, liveFrameRowCount))`, previous
capacity as `max(0, previousTerminalHeight - max(1, previousLiveFrameRowCount))`, and recovery
`maxRows` as the smaller capacity. The context SHALL expose positive current and previous dimensions
plus positive previous capacity whenever the provider is invoked. The minimum one-row footprint
SHALL reserve a physical cursor anchor for an empty retained frame.

#### Scenario: Live frame leaves viewport rows available
- **WHEN** the previous and prepared live frames each have 3 rows in terminals with height 10
- **THEN** the provider SHALL receive current and previous positive dimensions, `previousMaxRows` 7, and `maxRows` 7

#### Scenario: Viewport grows substantially
- **WHEN** a one-row live frame moves from a terminal of height 5 to a terminal of height 100
- **THEN** `previousMaxRows` and `maxRows` SHALL both be 4 rather than allowing 99 rows of older history to replay

#### Scenario: Overlay extends the live frame
- **WHEN** a visible overlay causes the final prepared retained frame to occupy additional rows
- **THEN** those rows SHALL reduce `maxRows` before the provider is invoked

#### Scenario: Typed retained control reserves rows
- **WHEN** a valid retained typed control reserves rows represented by the prepared frame
- **THEN** every reserved frame row SHALL count toward the live-frame row count and reduce recovery capacity

#### Scenario: Retained frame is empty
- **WHEN** the previous and prepared retained frames have zero semantic rows in terminals with height 10
- **THEN** Siglyph SHALL reserve one physical live-frame anchor row and the provider SHALL receive `previousMaxRows` and `maxRows` 9

#### Scenario: Live frame fills or exceeds viewport
- **WHEN** the live-frame physical footprint is greater than or equal to terminal height
- **THEN** recovery SHALL use zero rows, SHALL NOT invoke the provider, and SHALL redraw the live frame safely

#### Scenario: Provider returns empty output
- **WHEN** a provider is invoked with positive `maxRows` and returns no lines
- **THEN** recovery SHALL add no blank line or row transition and the retained frame SHALL be emitted as in the existing preserve-scrollback redraw

#### Scenario: Provider stays within budget
- **WHEN** the provider returns at most `maxRows` lines in oldest-to-newest order
- **THEN** Siglyph SHALL preserve that line order immediately before the retained live frame

#### Scenario: Provider exceeds budget
- **WHEN** the provider returns more than `maxRows` lines
- **THEN** Siglyph SHALL fail the resize candidate before terminal output rather than truncating, dropping, or partially publishing provider output

### Requirement: Applications select the semantic recovery tail
The recovery contract SHALL require the provider to use `context.previousWidth` and
`context.previousMaxRows` to select the application-owned semantic tail that could have occupied the
invalidated old viewport, reflow that tail at `context.width`, and return only its newest rows bounded
by `context.maxRows`. Siglyph SHALL NOT retain a transcript, infer emulator scrollback survivors,
request the complete history, or claim terminal-independent deduplication.

#### Scenario: Older history is already above the viewport
- **WHEN** older durable rows remain in preserved terminal scrollback and only a newer tail occupied the invalidated viewport
- **THEN** the provider SHALL return only that newer tail and Siglyph SHALL publish only the returned rows

#### Scenario: Semantic entry wraps at current width
- **WHEN** a durable entry wraps differently at the resized width
- **THEN** the provider MAY reflow its semantic model and SHALL return at most the newest `maxRows` resulting display rows without requiring Siglyph to retain prior rendered bytes

#### Scenario: Emulator reflows scrollback differently
- **WHEN** terminal-specific resize behavior preserves or moves physical rows differently from another emulator
- **THEN** Siglyph SHALL make no claim that it can inspect those survivors or eliminate duplicates the provider selected

### Requirement: Recovery providers are owner-serialized and retryable
Siglyph SHALL invoke the provider synchronously as application-controlled Render work on the existing single drain owner, outside lifecycle and terminal-write locks, and SHALL allow no concurrent application callback or component render.

#### Scenario: Provider requests follow-up work through captured application state
- **WHEN** provider code causes ordinary runtime work to be published while it owns Render work
- **THEN** that work SHALL be recorded for later owner processing without recursive drain or concurrent application execution

#### Scenario: Resize invalidates provider output
- **WHEN** resize generation, width, or height changes after live-frame or provider rendering but before publication
- **THEN** Siglyph SHALL publish none of the stale recovery/live candidate, preserve the committed semantic baseline, and schedule a forced recovery redraw at latest dimensions

#### Scenario: Provider is invoked again after invalidation
- **WHEN** a stale candidate is retried for latest dimensions
- **THEN** the provider MAY run more than once, exactly one latest candidate SHALL commit, and there SHALL be no owner-local retry loop

#### Scenario: Stop wins before recovery publication
- **WHEN** lifecycle leaves `Running` after provider rendering but before the synchronized publication boundary
- **THEN** the candidate SHALL publish nothing, queued ordinary recovery work SHALL be discarded, and cleanup SHALL not retain a recovery callback

### Requirement: Resize recovery is structurally text-only
`NormalResizeRecoveryProvider.render` SHALL return only `Vector[String]` ordinary lines. Recovery SHALL expose no `Component`, `ComponentRender`, `TerminalRenderControl`, `CursorPlacement`, raw trusted writer, or terminal-control encoder authority.

#### Scenario: Recovery line contains supported styling
- **WHEN** a returned line contains bounded valid ESC-form SGR or OSC 8 metadata
- **THEN** existing ordinary-line allowlisting SHALL preserve that supported metadata and close/reset it at line boundaries

#### Scenario: Recovery line resembles a terminal protocol
- **WHEN** a returned line contains image, cursor, CSI, non-OSC-8 OSC, APC, DCS, C0, DEL, C1, or other unsupported terminal-control-looking data
- **THEN** existing trusted-output sanitization SHALL keep it inert and SHALL infer no typed authority

#### Scenario: Recovery line exceeds current width
- **WHEN** a returned line has visible width greater than `context.width`
- **THEN** the existing ANSI- and Unicode-aware runtime safety path SHALL sanitize it to current width and increment aggregate sanitization accounting
- **AND** Siglyph SHALL NOT retain the provider source or sanitized line in the content-bearing last-sanitization sample

#### Scenario: Application needs typed recovery images
- **WHEN** an application wants Kitty, iTerm2, cleanup, or cursor metadata in recovery output
- **THEN** the first recovery API SHALL provide no such path and SHALL require a future separately specified ownership contract

### Requirement: Recovery and live frame commit as one owned redraw
For a valid current candidate, Siglyph SHALL clear and home only the active normal-screen viewport, emit recovery rows followed by the retained live frame in one synchronized TUI-owned render write, and SHALL omit `CSI 3 J`.

#### Scenario: Recovery prefix and live frame are committed
- **WHEN** a compatible resize candidate contains recovery rows and a live frame
- **THEN** terminal output order SHALL be synchronized-output start, viewport clear/home, required retained-control replacement cleanup, recovery rows, live-frame rows and controls, retained hardware cursor placement, synchronized-output end, and autowrap restoration

#### Scenario: No writer interleaves output
- **WHEN** input, append, control, query, render, or stop work is concurrently ready
- **THEN** no terminal bytes SHALL interleave between the recovery prefix and retained live frame

#### Scenario: Backend write fails
- **WHEN** the backend throws after the combined recovery/live write begins
- **THEN** Siglyph SHALL record runtime failure, perform normal terminal restoration, and SHALL NOT report successful recovery or claim rollback of bytes already accepted

### Requirement: Recovery remains outside retained semantic state
After a successful recovery redraw, Siglyph SHALL retain only the prepared live frame and its component-owned metadata as the differential baseline. Recovery lines SHALL remain detached one-shot durable output.

#### Scenario: Successful recovery commits baseline
- **WHEN** recovery rows and a live frame are written successfully
- **THEN** `previousFrame`, retained controls, selected cursor, base layout, overlay layouts, focus, and input targets SHALL describe only the live frame

#### Scenario: Ordinary differential redraw follows recovery
- **WHEN** retained live state changes after recovery
- **THEN** the differential renderer SHALL move and repaint relative to the live frame without repainting or clearing the recovered prefix

#### Scenario: Hardware cursor follows recovery
- **WHEN** hardware cursor positioning is enabled and the retained frame has a selected structured cursor
- **THEN** final cursor placement and logical cursor accounting SHALL remain relative to the relocated live frame

#### Scenario: Mouse routing follows recovery
- **WHEN** coordinate-aware mouse routing has a known frame origin and recovery precedes the live frame
- **THEN** Siglyph SHALL update the physical live-frame origin so retained bounds continue receiving correct terminal coordinates

#### Scenario: Live frame exceeds viewport
- **WHEN** recovery budget is zero because the live frame exceeds terminal height
- **THEN** existing scrolled frame-origin, cursor, and retained-layout behavior SHALL remain valid

#### Scenario: Empty live frame follows recovery
- **WHEN** recovery rows precede an empty retained frame
- **THEN** Siglyph SHALL leave the cursor on the reserved live-frame anchor below recovery so a later append does not clear or overwrite the final recovered row

### Requirement: Recovery diagnostics are bounded and redacted
Siglyph SHALL expose additive structured diagnostics for completed, stale-discarded, and failed recovery attempts, including only bounded outcome/failure category, strict row budget, recovered row count, and resize generation.

#### Scenario: Recovery commits successfully
- **WHEN** an eligible recovery/live redraw commits
- **THEN** diagnostics SHALL report completed outcome, `maxRows`, exact committed recovery row count including zero, and the committed resize generation

#### Scenario: Geometry discards a candidate
- **WHEN** latest geometry invalidates a rendered candidate before output
- **THEN** diagnostics SHALL report a stale/discarded structural outcome without reporting provider content

#### Scenario: Provider throws
- **WHEN** provider rendering throws
- **THEN** diagnostics SHALL classify provider failure without retaining the exception message or provider object

#### Scenario: Row budget is violated
- **WHEN** provider output exceeds `maxRows`
- **THEN** diagnostics SHALL classify row-budget validation failure with bounded counts and no line content

#### Scenario: Diagnostic content is redacted
- **WHEN** any recovery event is observed
- **THEN** it SHALL contain no transcript entry, rendered line, SGR/OSC source, exception message, raw byte, component, control, image payload, filename, or application object reference

### Requirement: Normal resize recovery is portable and documented
Siglyph SHALL implement eligibility, provider invocation, validation, planning, state commit, and diagnostics in canonical shared core for JVM and Scala Native without a new runtime dependency.

#### Scenario: JVM and Native execute the same recovery contract
- **WHEN** equivalent JVM and Scala Native TUI sessions receive the same semantic provider output and resize sequence
- **THEN** they SHALL use the same budget, ordering, baseline, stale-candidate, failure, and diagnostic semantics

#### Scenario: PTY conformance validates terminal boundary
- **WHEN** automated JVM PTY tests exercise recovery and a later append
- **THEN** they SHALL verify clear/recovery/live/append byte ordering, synchronized output, `CSI 3 J` absence, and terminal restoration without claiming emulator scrollback persistence

#### Scenario: Emulator persistence is claimed
- **WHEN** documentation or release validation claims preserved recovery behavior in Kitty, iTerm2, or another emulator
- **THEN** that claim SHALL come from documented manual smoke coverage naming the terminal and repeated width/height resize sequence

#### Scenario: Public API is documented
- **WHEN** recovery public types and options are added
- **THEN** Scaladoc and project documentation SHALL explain invocation, row ownership, retryability, provider speed expectations, terminal-dependent survivor limits, JVM/Native scope, text-only non-goals, and the absence of transcript retention
