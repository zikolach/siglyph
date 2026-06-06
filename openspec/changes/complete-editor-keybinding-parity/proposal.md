## Why

`scala-tui` has most advanced editing primitives, but several `pi-tui` editor/input keybinding behaviors are still hard-coded, incomplete, or undocumented. Completing parity gives applications predictable readline-style editing, configurable bindings, and upstream-compatible autocomplete/history interactions.

## What Changes

- Add a shared `KeybindingsManager`-style API for matching typed `TerminalInput` events to editing/autocomplete commands with `pi-tui` defaults.
- Complete remaining editor and input keybinding parity after re-verifying current upstream `pi-tui` behavior in `keybindings.ts` and `components/editor.ts`.
- Add editor prompt history behavior for Up/Down navigation where upstream uses history, including dedupe/max-size semantics if present upstream.
- Add PageUp/PageDown, jump-to-character, and remaining navigation/deletion/yank/autocomplete interactions supported by the typed input model.
- Document intentional deviations where terminal encodings cannot be represented portably on JVM/Native.
- Preserve existing editor APIs and avoid new runtime dependencies.

## Capabilities

### New Capabilities
- `keybinding-management`: defines backend-independent keybinding models, default `pi-tui` command mappings, custom override behavior, and conflict handling.

### Modified Capabilities
- `text-editing`: adds remaining editor/input command semantics, history navigation, page movement, jump-to-character, and callback expectations.
- `autocomplete`: clarifies keybinding precedence and interactions between autocomplete overlays and editor key commands.
- `terminal-runtime`: extends or documents typed input normalization for any remaining default bindings that depend on parser support.
- `developer-api`: adds public API/documentation requirements for configurable keybindings without adding runtime dependencies.

## Impact

- Affected code: terminal input parser/model, new keybinding model/manager in shared core, `Input`, `Editor`, autocomplete overlay routing, editor buffer/history helpers, tests, README/docs/smoke notes.
- Public API: additive keybinding configuration and command models; existing construction/callback APIs remain source-compatible.
- Dependencies: no new runtime dependencies.
- Platforms: JVM and Scala Native share command matching and editing behavior; platform-specific parser differences must be documented and tested where possible.
