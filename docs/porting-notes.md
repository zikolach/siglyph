# Porting Notes

`scala-tui` uses two upstream references:

1. **Current `pi-tui`** — canonical behavior source.
   - Repository: https://github.com/earendil-works/pi
   - Cached checkout used during bootstrap: `~/.cache/checkouts/github.com/earendil-works/pi`
   - Package path: `packages/tui`

2. **TauTUI** — Node-free architecture reference.
   - Repository: https://github.com/steipete/TauTUI
   - Cached checkout used during bootstrap: `~/.cache/checkouts/github.com/steipete/TauTUI`

## Sync procedure

When porting a feature:

1. Inspect the current `pi-tui` source and tests for the feature.
2. Inspect TauTUI for Node-free architecture ideas and edge cases.
3. Implement Scala APIs idiomatically, but document the corresponding `pi-tui` concept.
4. Add tests that mirror high-value `pi-tui` behavior.
5. Prefer normalized virtual-terminal assertions for semantic output.
6. Use raw ANSI snapshots only for critical renderer protocol behavior such as synchronized output, cursor movement, and clearing.
7. Document intentional deviations in this file or a feature-specific note.

## Intentional deviations

- Components receive typed terminal input events rather than raw escape strings where practical.
- Components may report a minimal `InputResult` from input handling. Overlay work now adds a small `TUIContext`/`OverlayHost` capability instead of passing concrete runtime internals into components.
- Multiline editing starts with a pure logical `EditorBuffer` and a rendered `Editor` MVP. The Scala editor consumes typed terminal input, renders a fake cursor inside component output, and keeps terminal display layout separate from the logical buffer.
- The rendered editor now ports wrapping, Unicode-aware cursor placement, core editing keys, callbacks, configurable Enter behavior, and overlay-backed autocomplete. Undo/kill-ring, large-paste markers, IME cursor markers, and hardware cursor positioning remain explicit follow-ups.
- `pi-tui` autocomplete uses async promises plus `AbortSignal`. `scala-tui` intentionally does not parameterize the full runtime with `F[_]`; it exposes a cancellable callback-style `AutocompleteProvider` boundary that applications can adapt from `Future`, file/network work, or external effect runtimes without adding core dependencies.
- `pi-tui` includes slash-command autocomplete helpers, but command execution belongs to the application. `scala-tui` mirrors that separation: slash-command helpers produce suggestions and completions only.
- Markdown is separated into a pluggable module instead of being part of the core module.
- JVM raw mode initially uses `stty` rather than JLine; JLine would require explicit dependency approval.
- Resize hardening intentionally improves on `pi-tui`'s stop-then-throw behavior for over-wide rendered lines: `scala-tui` keeps component width contracts testable, but the runtime sanitizes final over-wide output so normal interactive sessions survive narrow terminal resizes.
- Overlay positioning follows `pi-tui`'s hybrid model: width and max-height resolve against current terminal dimensions; absolute row/column values win over percentages, percentages win over anchors, offsets are applied, and margins clamp final placement.
