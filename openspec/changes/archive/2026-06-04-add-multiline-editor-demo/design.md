## Context

`scala-tui` now has a real interactive runtime, typed input events, a minimal input-result model, and a pure `EditorBuffer` for multiline text mutation. The next step is to render that buffer as an interactive component and prove it in JVM and Scala Native demos.

Upstream `pi-tui` has a richer editor with large paste markers, undo, kill ring, autocomplete, and overlay interactions. This change intentionally delivers only the rendered editor MVP needed to validate layout, cursor rendering, configurable Enter behavior, and demo integration.

## Goals / Non-Goals

**Goals:**

- Add a public `Editor` component that uses `EditorBuffer` for all text storage and mutations.
- Add public editor options and `EditorEnterBehavior` to configure Enter/newline/submit semantics.
- Support prompt-like mode where Enter submits and Shift+Enter inserts newline.
- Support editor-like mode where Enter inserts newline and Cmd/Super+Enter submits.
- Add a pure layout helper for wrapped visual lines and cursor placement.
- Render a fake cursor using inverse-video text, matching the existing single-line `Input` approach.
- Add shared editor demo construction usable by JVM and Scala Native launchers.
- Add Scaladoc, docs, tests, and Mill quality validation.

**Non-Goals:**

- No autocomplete provider or overlay suggestion UI.
- No undo stack, kill ring, or yank-pop behavior.
- No large-paste marker compaction/substitution.
- No IME cursor marker or hardware cursor positioning.
- No full `pi-tui` editor parity claim.
- No runtime dependencies.

## Decisions

### Decision: Use a visual layout helper separate from `EditorBuffer`

Add an editor-local pure layout helper, such as `EditorLayout`, that converts logical buffer lines into wrapped visual lines and maps the logical cursor to a visual row/column.

Rationale: `EditorBuffer` should remain a logical text model, independent of terminal width, display cells, wrapping, and ANSI rendering. A separate layout helper keeps wrapping/cursor tests pure and avoids burying display logic in the component.

Alternative considered: render directly from `EditorBuffer` in `Editor.render`. This is simpler initially, but cursor mapping bugs become harder to isolate and test.

### Decision: Render a fake cursor instead of moving the terminal cursor

The MVP editor will render the cursor as inverse-video text at the visual cursor position, including an inverse space at end-of-line or on empty lines.

Rationale: this matches `Input`, works with the existing `Vector[String]` component contract, and avoids introducing hardware cursor positioning or viewport-relative cursor APIs.

Alternative considered: extend `Focusable` to expose hardware cursor coordinates. That is likely useful later, but it adds runtime/rendering complexity not needed for this MVP.

### Decision: Make Enter behavior configurable

Introduce an `EditorEnterBehavior` public API. The design must support at least:

- submit on plain Enter, newline on Shift+Enter
- newline on plain Enter, submit on Cmd/Super+Enter

Rationale: prompt/chat TUIs and document editors have different expectations. The user explicitly chose configurable behavior, Cmd+Enter submit for newline-first mode, and Shift+Enter as the alternative newline key when Enter submits.

Alternative considered: fixed Enter behavior. That would simplify implementation but force one application style and make the demo less representative.

### Decision: Rely on typed modified Enter events when available

The editor will act on normalized `TerminalInput.Key(TerminalKey.Enter, KeyModifiers(...))` events. Parser/backend improvements for terminals that cannot emit modified Enter are out of scope for this change.

Rationale: the user confirmed the current parser can reliably produce normalized modified Enter events for this change. The editor should consume typed input, not raw escape strings.

### Decision: Keep the demo shared and backend-specific launchers thin

Add shared editor demo construction and reuse it from JVM and Native launchers. Existing backend launchers should differ only in terminal backend selection.

Rationale: this preserves JVM/Native parity and avoids duplicating demo state/input logic.

## Risks / Trade-offs

- **Risk: Modified Enter varies by terminal** → Tests will use typed `TerminalInput` events; docs will state that modified Enter depends on terminal/parser normalization.
- **Risk: Wide grapheme cursor drift** → Add pure layout tests for CJK, combining marks, emoji, and wrapped lines.
- **Risk: Editor scope grows toward full `pi-tui` parity** → Explicitly defer undo, kill ring, autocomplete, overlays, and large-paste markers.
- **Risk: Fake cursor is not a real terminal cursor** → Accept for MVP; revisit hardware cursor positioning after editor/overlay pressure is clearer.
- **Risk: Demo focus complexity** → Keep demo small and focused on the editor; use existing TUI exit handling and shared demo construction.
