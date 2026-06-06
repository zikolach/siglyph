## Context

`Input` and `Editor` already render a fake inverse-video cursor, and the recent `CursorMarker.Sequence` support gives focused editing components a zero-width marker adjacent to that fake cursor. The missing piece is runtime support: the final TUI output still contains no hardware cursor movement derived from those markers, so IME candidate windows and terminal-native cursor affordances cannot reliably follow the logical editing cursor.

The TUI renderer is differential, overlay-aware, ANSI-aware, and shared across JVM and Scala Native through backend abstractions. Hardware cursor positioning must therefore happen after base content and overlays have been composited and sanitized, but before writing bytes that could expose marker sequences to the terminal.

## Goals / Non-Goals

**Goals:**
- Add an opt-in runtime option that enables hardware cursor placement from cursor markers.
- Preserve fake cursor rendering as the primary visible cursor, matching current component behavior.
- Strip all cursor marker sequences from terminal output, virtual terminal snapshots, and stop positioning calculations.
- Compute row/column using display width while ignoring ANSI/control sequences and preserving Unicode grapheme display-width behavior.
- Keep behavior shared, dependency-free, and testable through `VirtualTerminal`.

**Non-Goals:**
- Replacing fake cursor rendering with terminal cursor rendering.
- Adding terminal IME APIs beyond standard cursor placement.
- Adding platform-specific cursor behavior in JVM or Native backends.
- Changing autocomplete overlay ownership or focus semantics except insofar as final marker scanning respects the composited frame.

## Decisions

1. **Scan final composited output, not component-local output.**
   - Rationale: overlays may cover or replace base component cells, and runtime sanitization may alter the final frame. The hardware cursor must correspond to what is actually written.
   - Alternative considered: components report cursor coordinates directly. That would require every container/overlay/layout owner to transform coordinates and would duplicate existing composition logic.

2. **Treat marker removal as part of frame preparation.**
   - Rationale: the terminal must never see marker control sequences as visible or protocol output, and differential comparisons should use cleaned frame content to avoid marker-only churn.
   - Alternative considered: write the frame first and then erase markers. This risks leaking nonstandard sequences and corrupting visible output.

3. **Keep hardware cursor positioning opt-in.**
   - Rationale: fake cursor behavior is already stable and deterministic, while terminals vary in how visible hardware cursors interact with inverse-video fake cursors, IME, and scrollback. Applications can enable the feature when they need it.
   - Alternative considered: enable by default for focused editors. This could change user-visible cursor behavior in existing demos/applications.

4. **Use a shared marker scanner result.**
   - Rationale: `TUI` needs both cleaned lines and an optional cursor cell. A small pure helper such as `CursorMarker.stripAndLocate(lines)` can be unit-tested without terminal backends and reused by virtual terminal tests.
   - Alternative considered: inline scanning in `TUI.render`. That would couple marker semantics to the renderer and make edge cases harder to test.

5. **Resolve multiple markers deterministically but treat them as invalid component behavior.**
   - Rationale: normally only the focused input target emits a marker. If multiple markers survive composition, the scanner should select the first marker in row-major order for deterministic behavior and strip all markers; tests should cover stripping, not rely on multi-marker placement.

## Risks / Trade-offs

- [Risk] ANSI parsing mistakes could compute a wrong column. → Mitigation: reuse existing ANSI-aware visible-width utilities and add tests with SGR, OSC hyperlinks, wide Unicode, combining marks, and marker placement before end-of-line fake cursors.
- [Risk] Differential rendering may compare frames with markers and cause unnecessary redraws. → Mitigation: strip markers before storing/comparing rendered frame state.
- [Risk] Hardware cursor movement after partial redraw could use the wrong origin. → Mitigation: perform cursor movement through the existing terminal cursor abstraction relative to the known TUI frame origin after each frame write.
- [Risk] Terminal cursor visibility may conflict with fake cursor. → Mitigation: keep the feature disabled by default and preserve existing cursor show/hide lifecycle.

## Migration Plan

- Add the public option with a disabled default.
- Implement and test marker scanning/stripping in pure core code.
- Wire the cleaned frame and optional cursor position into the TUI render path.
- Update docs/smoke notes so applications know when to enable hardware cursor positioning.
- Rollback is disabling the option; fake cursor rendering remains unchanged.

## Open Questions

- Should a future release enable this by default after sufficient interactive testing across common terminals?
- Should debug tooling expose the selected marker position when multiple markers are stripped?
