## Why

`scala-tui` currently has a multiline editor and resize-safe runtime, but it lacks the generic overlay stack and autocomplete contracts needed for `pi-tui` parity. Adding overlays now provides the foundation for editor suggestions, command palettes, menus, dialogs, and future modal UI while keeping terminal rendering robust under resize and narrow widths.

## What Changes

- Add a generic, handle-based overlay stack to the TUI runtime, modeled on `pi-tui` hybrid positioning semantics.
- Add public overlay types for size values, anchors, margins, visibility predicates, focus-capturing behavior, and overlay handles with stable internal identifiers.
- Add a public TUI context/host abstraction exposing render, exit, focus, and overlay capabilities to components without requiring concrete `TUI` coupling.
- Route keyboard input to the topmost visible focus-capturing overlay, while non-capturing overlays preserve the previous focused component.
- Composite visible overlays over base content using ANSI-safe rectangular replacement; overlay spaces are literal cells and overlay lines are clipped/sanitized to bounds.
- Re-resolve and clamp overlay layouts on every render so resize and narrow terminals remain safe.
- Add autocomplete provider models and a cancellable async-capable callback boundary without introducing third-party runtime dependencies or parameterizing `TUI`/`Component` by an effect type.
- Integrate editor autocomplete with the overlay stack, including suggestion display, keyboard navigation, cancel, and completion application.
- Provide application-supplied slash-command helper models/providers as autocomplete utilities; command semantics remain owned by the application, not the TUI runtime.
- Update interactive demos and documentation to exercise overlay rendering and editor autocomplete on JVM and Scala Native.

## Capabilities

### New Capabilities
- `autocomplete`: Public autocomplete request/result/provider contracts, cancellable request handles, slash-command helper models, and completion application semantics.

### Modified Capabilities
- `component-rendering`: Overlay stack composition, hybrid positioning, focus behavior, z-order, clipping, and render-width guarantees become concrete runtime behavior.
- `developer-api`: Public overlay, context/host, and autocomplete APIs are added while preserving Node-free, dependency-light JVM/Native compatibility.
- `terminal-runtime`: Live runtime input routing, resize redraws, and virtual terminal testing are extended to cover overlays and overlay focus behavior.
- `text-editing`: The editor gains autocomplete integration backed by the overlay stack and provider contracts.

## Impact

- Affected core APIs: `TUI`, `Component`, focus/context abstractions, overlay types, autocomplete types, `Editor`, `EditorOptions`, and likely `SelectList` integration.
- Affected tests: core rendering/TUI tests, virtual terminal assertions, autocomplete provider tests, editor autocomplete tests, and interactive demo tests.
- Affected demos/docs: README, interactive smoke docs, porting notes, and shared JVM/Native interactive demo behavior.
- Dependencies: no new third-party runtime dependencies; async autocomplete uses a cancellable callback contract and may expose standard-library adapters only.
