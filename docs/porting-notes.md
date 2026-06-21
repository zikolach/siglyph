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
- The rendered editor now ports wrapping, Unicode-aware cursor placement, core editing keys, callbacks, configurable Enter behavior, overlay-backed autocomplete, `pi-tui`-aligned undo-only/kill-ring/yank/yank-pop commands, large-paste markers, focused cursor marker emission, and opt-in runtime hardware cursor positioning from final-frame marker scanning. Scala keeps autocomplete overlay-backed while matching `pi-tui`'s user-visible editor-adjacent suggestion placement; the hardware cursor remains opt-in and preserves fake cursor rendering.
- `pi-tui` autocomplete uses async promises plus `AbortSignal`. `scala-tui` intentionally does not parameterize the full runtime with `F[_]`; it exposes a cancellable callback-style `AutocompleteProvider` boundary that applications can adapt from `Future`, file/network work, or external effect runtimes without adding core dependencies.
- `pi-tui` includes slash-command autocomplete helpers, but command execution belongs to the application. `scala-tui` mirrors that separation: slash-command helpers produce suggestions and completions only.
- `scala-tui` includes a `CombinedAutocompleteProvider` for slash commands, dependency-free filesystem path/attachment completion, and application-owned natural trigger prefixes such as `#`. Command and trigger semantics remain application-owned: providers insert replacement text only. Built-in filesystem enumeration uses Java/NIO through `FileSystemPathCompletionProvider` and requires no external shell tools such as `fd` or `find`. Fuzzy ranking is opt-in via `AutocompleteFuzzyRanking.Enabled`; refresh debounce is injectable via `EditorAutocompleteDebouncer`, and stale async results are ignored through cancellable request handles.
- Markdown is separated into a pluggable module instead of being part of the core module. The baseline renderer is dependency-free and intentionally smaller than `pi-tui`'s `marked`-based renderer. It supports theme hooks, readable fallback links, OSC 8 links when configured with hyperlink-capable terminal settings, optional fenced-code highlighter hooks, and parser adapters behind the `MarkdownParser` boundary. Richer parser dependencies remain optional and require approval.
- Image protocol helpers are dependency-free in core, while the `Image` component lives in the optional `image` module. The image module now provides file/byte helpers that return base64 data, MIME type, and PNG/JPEG/GIF/WebP dimensions, plus cell-size bounding helpers. Scaling and transcoding remain deferred to optional dependency-backed modules.
- Terminal runtime now sends a cell-size query (`ESC[16t`) on start and consumes valid terminal replies (`ESC[6;H;Wt`) for runtime image sizing. `ImageRenderOptions` uses fixed `ImageCellDimensions` by default; callers opt into queried dimensions with `ImageCellDimensionsSource.Runtime`. When reply data is missing or invalid, runtime image layout keeps deterministic fallback dimensions (`9x18`).
- Image rendering keeps cursor-safe layout by reserving protocol-reported rows before subsequent content in component output, including Kitty and iTerm2 protocols.
- JVM raw mode initially uses `stty` rather than JLine; JLine would require explicit dependency approval.
- Resize hardening intentionally improves on `pi-tui`'s stop-then-throw behavior for over-wide rendered lines: `scala-tui` keeps component width contracts testable, sanitizes final over-wide output, and repaints resize frames in place so normal interactive sessions survive narrow terminal resizes without clearing scrollback.
- Overlay positioning follows `pi-tui`'s hybrid model: width and max-height resolve against current terminal dimensions; absolute row/column values win over percentages, percentages win over anchors, offsets are applied, and margins clamp final placement.
- `TruncatedText` mirrors `pi-tui`'s first-line-only, ANSI-aware truncating status/header widget, while keeping output width-safe for narrow terminal rendering.
- `SettingsList` ports the core settings-row behavior with typed `TerminalInput`, value cycling, descriptions, scroll indicators, cancellation callbacks, optional containment filtering, optional fuzzy ranking, and application-provided submenus through existing overlay contracts.
- `Loader` and `CancellableLoader` are available as shared-core, tick-driven components. They port the visible indicator/message rendering, message and indicator mutation, idempotent lifecycle, Escape cancellation, and cancellation state, but intentionally do not own a Node-style `setInterval` timer.
- `CancellableLoader` exposes a Scala `CancellationToken` plus `cancel()`/`onCancel` instead of JavaScript `AbortSignal`; applications adapt that token to their own async/effect runtime when needed.
- Advanced keyboard support stays typed. `TerminalInput.KeyEvent` models press, repeat, and release metadata while the two-argument `TerminalInput.Key` constructor and pattern remain press-compatible for existing components. Release events are routed only to components that opt in with `wantsKeyRelease`.
- JVM and Native backends expose conservative Kitty keyboard protocol negotiation hooks. The current implementation does not add unsafe platform modifier probing; when modified Enter or similar platform state cannot be queried safely, the parser emits the ordinary key event.
- Terminal title and progress are optional backend capabilities rather than required `Terminal` methods. `TUI.setTerminalTitle` and `TUI.setTerminalProgress` return `false` on unsupported terminals such as `StreamTerminal` and emit no unsupported escape sequences.
- Terminal background color and color-scheme queries are owned by `TUI`, not individual backends. Backends write query bytes and deliver raw replies; `TUI` parses OSC 11 and color-scheme reports, applies timeouts, consumes protocol replies before component input routing, and returns `None` when no valid reply arrives.
