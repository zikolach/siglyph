## Context

The baseline Markdown module is dependency-free and uses a small block model. Current list parsing normalizes unordered markers to `-` and ordered markers to sequential numbers. Upstream `pi-tui` preserves more source list detail in rendered output, especially for chat-style Markdown with `+`, `*`, `-`, ordered markers, task markers, loose-list spacing, and wrapped continuation lines.

## Goals / Non-Goals

**Goals:**

- Keep current normalized rendering as the default public behavior.
- Add an explicit option to preserve source list markers where the baseline parser can identify them.
- Preserve task-list markers as text in list items.
- Preserve loose-list blank lines in baseline-supported cases.
- Improve continuation indentation when list items wrap.
- Keep output ANSI-aware and width-safe.

**Non-Goals:**

- No mandatory Markdown parser dependency.
- No full CommonMark compliance claim.
- No semantic task-list model or checkbox interaction.
- No changes to `core`.

## Decisions

### 1. Extend baseline list metadata only as needed

Represent the source marker for each parsed list item when available. The renderer uses that marker only when marker preservation is enabled. This avoids changing default output while supporting parity mode.

Alternatives considered:

- Store lists as plain item strings only: rejected because marker identity cannot be recovered later.
- Replace the baseline parser with a third-party parser: rejected because dependency and platform approval are out of scope.

### 2. Preserve task markers as text

Task list markers such as `[ ]` and `[x]` remain visible text in the item. The renderer does not expose task state or interactive checkbox APIs in this change.

Alternatives considered:

- Add a semantic task-list model: rejected because it expands the public model before interactive task behavior is needed.

### 3. Make marker preservation opt-in

Default rendering keeps normalized markers to preserve current public behavior. Applications that need upstream-style source fidelity enable the option.

Alternatives considered:

- Change the default to source preservation: rejected because it can change visible output for existing users.

## Risks / Trade-offs

- Parser metadata changes can leak into public APIs → keep new fields minimal and document defaults.
- Loose-list parsing can grow into full CommonMark work → support only test-backed baseline cases and document deferred cases.
- Wrapped indentation can break width safety → add tests for narrow widths, ANSI styling, ordered markers, unordered markers, and task markers.
