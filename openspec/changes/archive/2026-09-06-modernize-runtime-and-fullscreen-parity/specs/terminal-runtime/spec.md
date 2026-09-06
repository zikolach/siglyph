## ADDED Requirements

### Requirement: Unexpected terminal worker failure propagation
Interactive and stream terminal backends SHALL report each unexpected input-reader, fragment-flush, and resize-worker failure to the owning TUI lifecycle at most once per worker and active generation. Independent worker failures SHALL remain reportable. Expected EOF, interruption caused by stop, and stale generation termination SHALL remain normal lifecycle outcomes.

#### Scenario: Input reader fails while running
- **WHEN** the active backend input reader throws an unexpected failure
- **THEN** the TUI records that failure, wakes `run()`, performs idempotent cleanup, reaches `Stopped`, and rethrows the recorded failure from `run()`

#### Scenario: Flush worker fails while running
- **WHEN** the active incomplete-input flush worker throws an unexpected failure
- **THEN** the backend reports it to the TUI instead of silently ending fragment flushing

#### Scenario: Resize worker fails while running
- **WHEN** the active resize worker can no longer poll or publish dimensions because of an unexpected failure
- **THEN** the backend reports it to the TUI instead of leaving a logically running session without resize delivery

#### Scenario: Stop interruption is not a failure
- **WHEN** explicit stop interrupts an active backend worker
- **THEN** the worker terminates without publishing a runtime failure

#### Scenario: First failure wins
- **WHEN** more than one backend worker reports failure during the same lifecycle
- **THEN** the TUI preserves one primary runtime failure, attaches later failures without duplicate cleanup, and wakes all lifecycle waiters

### Requirement: Shared runtime and screen rendering policies
The TUI SHALL keep lifecycle, ordered ingress, queries, overlays, focus, terminal output, and cleanup in shared runtime services while delegating frame preparation and differential output decisions to a normal-screen or viewport rendering policy. Existing public normal-screen behavior SHALL remain the default.

#### Scenario: Existing construction uses normal-screen policy
- **WHEN** an application constructs `TUI(terminal)`
- **THEN** it uses the existing normal-screen behavior without entering alternate screen or requiring a layout root

#### Scenario: Viewport construction uses viewport policy
- **WHEN** an application explicitly creates or configures a fullscreen viewport TUI
- **THEN** shared lifecycle and input services remain the same while viewport rendering owns fixed-height layout and scrolling

#### Scenario: Runtime failure restores either policy
- **WHEN** a runtime failure occurs under either screen rendering policy
- **THEN** the same single-owner cleanup path restores cursor, protocols, autowrap, and screen state exactly once

### Requirement: Instance-scoped terminal capability overrides
Terminal capability resolution SHALL allow per-runtime or per-component overrides for true color, OSC 8 hyperlinks, and image protocol without process-global mutable state. Explicit overrides SHALL take precedence over detected values and SHALL remain isolated between concurrent TUI instances.

#### Scenario: Explicit image override wins
- **WHEN** one runtime explicitly disables image support in an image-capable detected terminal
- **THEN** components attached to that runtime use fallback rendering without changing another runtime's capabilities

#### Scenario: Partial override preserves detection
- **WHEN** an application overrides hyperlinks but leaves true color and images unspecified
- **THEN** capability resolution uses the explicit hyperlink value and detected values for the remaining capabilities

#### Scenario: Concurrent overrides remain isolated
- **WHEN** two TUI instances use different capability overrides
- **THEN** each instance and its attached components observe only its own effective capabilities

### Requirement: Current terminal detection
Built-in terminal capability detection SHALL cover the current documented terminal set, including Zed, while remaining conservative for unknown terminals and multiplexers.

#### Scenario: Zed capability is detected
- **WHEN** environment data identifies a supported Zed terminal session outside an unsupported multiplexer
- **THEN** effective capabilities report the tested true-color, hyperlink, and image protocol support for Zed

#### Scenario: Multiplexer restriction wins
- **WHEN** a recognized terminal runs through a multiplexer path where a capability is not known to be forwarded
- **THEN** detection disables that capability unless the current runtime has an explicit override

### Requirement: Mouse tracking protocol lifecycle
Interactive terminal backends SHALL select, enable, and disable the minimum xterm mouse tracking mode required by configured behavior together with SGR coordinates. Basic input SHALL use normal tracking mode `1000`, drag motion SHALL use button-motion mode `1002`, and hover or uncaptured movement SHALL use all-motion mode `1003` only when explicitly requested and allowed by conservative multiplexer policy.

#### Scenario: Drag enables button motion
- **WHEN** a TUI enables drag-capable mouse input without hover movement
- **THEN** its interactive backend enables mode `1002` and SGR coordinates before delivery and disables both during cleanup

#### Scenario: Hover enables all motion
- **WHEN** a TUI explicitly enables uncaptured pointer movement on a supported direct terminal
- **THEN** its interactive backend enables mode `1003` and SGR coordinates for that lifecycle

#### Scenario: Multiplexer uses conservative motion
- **WHEN** the session runs through a multiplexer where all-motion forwarding is not supported or safe
- **THEN** capability resolution does not enable mode `1003` unless an explicit instance override permits it

#### Scenario: Mouse cleanup remains idempotent
- **WHEN** stop or runtime failure follows any enabled mouse tracking mode
- **THEN** the backend disables the selected tracking mode and SGR coordinates at most once for the completed cleanup obligation
