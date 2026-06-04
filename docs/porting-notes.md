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
- Components may report a minimal `InputResult` from input handling; richer focus and overlay command APIs are intentionally deferred until autocomplete/overlay work creates concrete pressure.
- Multiline editing starts with a pure logical `EditorBuffer` before a rendered editor component, keeping buffer mutation independent from terminal display layout.
- Markdown is separated into a pluggable module instead of being part of the core module.
- JVM raw mode initially uses `stty` rather than JLine; JLine would require explicit dependency approval.
