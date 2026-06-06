## Why

`scala-tui` has a strong rendered editor and runtime foundation, but several high-value `pi-tui` component behaviors remain missing or incomplete for parity and practical adoption. In particular, input editing behavior, autocomplete convenience, markdown rendering, and inline media support are still behind upstream expectations, which blocks drop-in migration from richer `pi-tui` apps. This change closes those gaps with a focused parity batch that stays backend-agnostic and dependency-light.

## What Changes

- Extend `Input` and `Editor` behavior to match upstream editing interactions (undo-only stack parity, kill-ring, word navigation/deletion, and related `pi-tui` default keybinding semantics such as `Ctrl+-` for undo).
- Add large-paste marker visualization and expansion behavior for `Editor` so large blocks can be shown compactly without losing logical text.
- Expand `Editor` and `Input` completion workflows with a combined provider that supports slash commands and path/file completions, including explicit trigger behavior and deterministic completion application.
- Implement a dependency-free basic markdown rendering path under the existing pluggable `markdown` module and keep parser boundaries open for optional JVM/Native third-party adapters.
- Add image protocol/capability support for Kitty/iTerm2 escape image emission plus an optional image component/helper module with clear fallback behavior when unsupported.
- Add optional IME/hardware-cursor support behavior (cursor marker and placement semantics) and related API hooks for downstream terminal integrations.
- Update demos, tests, and documentation to exercise the new capabilities and preserve existing non-breaking behavior.

## Capabilities

### New Capabilities
- `image-rendering`: Component and protocol behavior for rendering terminal images via Kitty/iTerm2-compatible escape paths with readable fallbacks.

### Modified Capabilities
- `text-editing`: Extend single-line and multiline editing requirements with `pi-tui`-aligned undo/kill-ring/keybinding/word navigation semantics, large-paste markers, and IME/hardware-cursor behavior.
- `autocomplete`: Add combined slash + file completion provider and deterministic path-trigger parsing.
- `markdown-rendering`: Move from placeholder/pluggable structure to concrete dependency-free basic behavior for rendered markdown output, width-safe fallback behavior, and optional parser adapter boundaries.
- `terminal-runtime`: Extend protocol/runtime expectations for image-capable output and hardware-cursor-aware editor interactions.
- `developer-api`: Expand public component and helper APIs to expose the new editing/parsing/media capabilities without adding mandatory runtime dependencies.

## Impact

- **Code areas:** `core/src/scalatui/components`, `core/src/scalatui/autocomplete`, `core/src/scalatui/editing`, `core/src/scalatui/terminal`, `core/src/scalatui/core`, `core/src/scalatui/syntax` (as needed), `markdown/src`, optional image/markdown adapter modules as approved, demos, and tests.
- **APIs:** Adds/extends public types for advanced editing utilities, completion providers, markdown/image helper contracts, and optional cursor marker behavior.
- **Tests:** New focused unit/integration tests for unicode editing behavior, completion parsing/applied completions, markdown render output, protocol/path branches, and protocol-dependent image rendering fallback behavior.
- **Dependencies:** No new mandatory runtime dependencies; optional Markdown/image adapter dependencies require explicit library approval and must not affect core or baseline modules. Keep runtime dependency-light and maintain JVM + Scala Native compatibility.
- **Docs:** Update README, porting/parity docs, and smoke/interactive docs for new behavior and controls.
