## Context

`scala-tui` currently enforces the component width contract by throwing if any rendered line exceeds terminal width. That is useful for tests, but manual interactive smoke testing showed an over-wide line can crash a backend input thread during resize/narrow-width rendering. Current runtime behavior also tracks width changes but not height changes, and interactive JVM/Native backends do not actively emit live resize notifications.

Upstream `pi-tui` is the canonical behavior source. It uses positive dimension fallbacks, many component-level `Math.max(1, ...)` width guards, stdout resize events, and full redraws for width/height changes. It still throws after stopping for over-wide non-image lines. This change intentionally keeps the component contract strict for tests and diagnostics, but improves runtime robustness by sanitizing final output instead of crashing normal interactive sessions.

## Goals / Non-Goals

**Goals:**

- Clamp render width and height to at least 1 before rendering.
- Track previous width and previous height in `TUI`.
- Full-redraw when width or height changes.
- Sanitize final non-image rendered lines to terminal width before writing.
- Preserve diagnostics for sanitized over-wide lines so component bugs remain discoverable.
- Prevent normal input/resize rendering from throwing uncaught exceptions on backend threads.
- Add live resize notifications to JVM and Scala Native interactive backends without runtime dependencies.
- Keep project demos width-safe at narrow terminal widths.
- Add regression tests for narrow widths, width resize, height resize, sanitized over-wide output, and resize notifications where practical.

**Non-Goals:**

- No overlay/autocomplete implementation.
- No hardware cursor or viewport-top parity beyond height-aware full redraws.
- No Windows support.
- No new runtime dependencies.
- No guarantee that third-party component bugs are semantically corrected; runtime sanitization only protects terminal sessions from invalid output width.

## Decisions

### Decision: Clamp dimensions in TUI

Before rendering, `TUI` will use `math.max(1, terminal.columns)` and `math.max(1, terminal.rows)`.

Rationale: This mirrors `pi-tui`'s effective positive fallback behavior and protects against transient zero dimensions during resize races or test fakes.

Alternative considered: Require all terminals to return positive dimensions. Backends should still do that where possible, but `TUI` is the final safety boundary.

### Decision: Sanitize final output instead of throwing during normal rendering

`TUI` will ANSI-safely truncate final rendered lines whose visible width exceeds the clamped render width before applying line resets and writing to the terminal. The component contract remains normative, and tests can still exercise components directly.

Rationale: The user explicitly wants the terminal library to be a solid building block that does not crash terminal sessions. This intentionally improves on `pi-tui`'s stop-then-throw behavior for normal runtime rendering.

Alternative considered: Keep fail-fast throws and only restore terminal state first. That matches `pi-tui` more closely but still crashes applications during resize/narrow-terminal conditions.

### Decision: Keep diagnostics lightweight and dependency-free

The runtime should expose or record enough information to make sanitization visible in tests/debugging, such as a counter, last diagnostic, or debug log controlled by environment/configuration. It should not add logging dependencies.

Rationale: Silent truncation can hide component bugs, but adding a logging stack is out of scope and violates dependency-light goals.

### Decision: Add live resize notification through polling

Use dependency-light polling for JVM and Scala Native interactive backends:

- JVM `SttyTerminal`: periodically query `stty size < /dev/tty`, update cached dimensions, invoke `onResize` when changed.
- Native `PosixTerminal`: periodically query `ioctl(TIOCGWINSZ)`, update cached dimensions, invoke `onResize` when changed.

Rationale: Polling avoids JVM-internal signal APIs and unsafe Scala Native signal-handler complexity while providing live resize behavior on macOS/Linux.

Alternative considered: Use SIGWINCH handlers. That is closer to Unix event semantics, but riskier in Scala Native and less dependency-light/portable on the JVM.

### Decision: Coalesce resize-triggered rendering through existing TUI render requests

Backend resize notifications should call the existing `onResize` callback. `TUI` should request a forced render and flush safely. If future work introduces a dedicated event loop, this can move to queued rendering.

Rationale: This keeps the implementation focused while adding the missing behavior.

## Risks / Trade-offs

- **Risk: Runtime truncation hides component bugs** → Keep component width contract in specs/tests and add diagnostics for sanitized lines.
- **Risk: Polling adds background threads** → Start polling only for interactive backends, use modest intervals, and stop threads idempotently.
- **Risk: Rendering from input/resize threads can still expose concurrency issues** → Keep synchronization in `TUI`, avoid uncaught render exceptions for width overflow, and add tests around render safety.
- **Risk: Height-aware redraw is still less complete than `pi-tui` viewport tracking** → Full redraw on height changes is the MVP; viewport-top/hardware-cursor parity remains follow-up work.
- **Risk: Very narrow terminals produce barely useful UI** → Runtime guarantees valid terminal output; demos/components may also add narrow-width fallback copy where appropriate.
