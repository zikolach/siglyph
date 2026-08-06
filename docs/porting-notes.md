# Porting Notes

`siglyph` uses two upstream references:

1. **Pinned `pi-tui` review baseline** — canonical behavior source for the current matrix.
   - Repository: https://github.com/earendil-works/pi
   - Reviewed commit: `da840b6216578c2a571d0374ac6a2091a83f9d91` (2026-09-05)
   - Cached checkout used during bootstrap: `~/.cache/checkouts/github.com/earendil-works/pi`
   - Package path: `packages/tui`
   - Maintained matrix: [pi-tui-compatibility.md](pi-tui-compatibility.md)

2. **TauTUI** — Node-free architecture reference.
   - Repository: https://github.com/steipete/TauTUI
   - Cached checkout used during bootstrap: `~/.cache/checkouts/github.com/steipete/TauTUI`

## Sync procedure

When porting a feature:

1. Pin and record the `pi-tui` revision, then inspect its source and tests for the feature.
2. Inspect TauTUI for Node-free architecture ideas and edge cases.
3. Implement Scala APIs idiomatically, but document the corresponding `pi-tui` concept.
4. Add tests that mirror high-value `pi-tui` behavior.
5. Prefer normalized virtual-terminal assertions for semantic output.
6. Use raw ANSI snapshots only for critical renderer protocol behavior such as synchronized output, cursor movement, and clearing.
7. Document intentional deviations in this file or a feature-specific note.

## Fullscreen baseline at the pinned revision

The pinned upstream tree includes `TuiAltScreen`, `HStack`, `VStack`, `ScrollView`, `MouseRegion`, transcript search, selection, and Kitty image clipping. Siglyph now has corresponding height-aware APIs through `TUI.fullscreen`. The exact Full, Partial, Deviation, and Extension classifications and evidence remain in [pi-tui-compatibility.md](pi-tui-compatibility.md).

Three runtime paths remain distinct:

- `TUI(terminal)` uses the normal terminal screen and unbounded width-only rendering.
- `TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))` changes only the screen buffer lifecycle. It remains the legacy width-only renderer.
- `TUI.fullscreen(terminal, layoutRoot)` enters the alternate screen and resolves a fixed-height layout root with stacks, scrolling, search, mouse gestures, selection, and typed image clipping.

Fullscreen is not a drop-in TypeScript API. It does not add runtime renderer switching, Windows support, Node scheduling, mandatory operating-system clipboard integration, or built-in LaTeX parsing.

## Intentional deviations

- Components receive typed terminal input events rather than raw escape strings where practical.
- `pi-tui` raw global input listeners map to typed `TUI.addInputListener` callbacks. A listener returns `InputResult.Ignored` to continue normal focused routing, `InputResult.Handled(...)` to consume the input, or `InputResult.Exit` to stop through the normal shutdown path.
- Components may report a minimal `InputResult` from input handling. Overlay work now adds a small `TUIContext`/`OverlayHost` capability instead of passing concrete runtime internals into components.
- Multiline editing starts with a pure logical `EditorBuffer` and a rendered `Editor` MVP. The Scala editor consumes typed terminal input, renders a fake cursor inside component output, and keeps terminal display layout separate from the logical buffer.
- The rendered editor now ports wrapping, Unicode-aware cursor placement, core editing keys, callbacks, configurable Enter behavior, overlay-backed autocomplete, `pi-tui`-aligned undo-only/kill-ring/yank/yank-pop commands, large-paste markers, structured focused cursor metadata, and opt-in runtime hardware cursor positioning. Scala keeps autocomplete overlay-backed while matching `pi-tui`'s user-visible editor-adjacent suggestion placement; the hardware cursor remains opt-in and preserves fake cursor rendering. Former cursor APC bytes are ordinary visible inert text and cannot influence cursor placement.
- Editor input is typed. `siglyph` does not emulate `pi-tui`'s raw standalone-backslash Enter workaround; newline behavior is represented by `EditorEnterBehavior` and typed Enter keybindings. The `Ctrl+J` newline alias is supported when reported as distinguishable typed Ctrl+J input, while a bare line-feed byte remains plain Enter because terminal Return encodings can be ambiguous.
- `pi-tui`'s `insertTextAtCursor` maps to the Scala-idiomatic `Editor.insertAtCursor(text)` method. It uses editor-buffer insertion, newline normalization, large-paste markers, one undo snapshot, change callbacks, active autocomplete refresh, and TUI render requests when attached.
- `pi-tui` autocomplete uses async promises plus `AbortSignal`. `siglyph` intentionally does not parameterize the full runtime with `F[_]`; it exposes a cancellable callback-style `AutocompleteProvider` boundary that applications can adapt from `Future`, file/network work, or external effect runtimes without adding core dependencies.
- `pi-tui` includes slash-command autocomplete helpers, but command execution belongs to the application. `siglyph` mirrors that separation: slash-command helpers produce suggestions and completions only.
- `pi-tui` can auto-apply a single force-file autocomplete suggestion. `siglyph` keeps autocomplete selection explicit by default and enables single-result forced auto-apply only when `EditorOptions(autoApplySingleForcedCompletion = true)` is configured.
- `siglyph` includes a `CombinedAutocompleteProvider` for slash commands, dependency-free filesystem path/attachment completion, and application-owned natural trigger prefixes such as `#`. Command and trigger semantics remain application-owned: providers insert replacement text only. Built-in filesystem enumeration uses Java/NIO through `FileSystemPathCompletionProvider` and requires no external shell tools such as `fd` or `find`. The legacy base directory remains both current directory and containment root; separate current/root settings plus parent, home, and absolute policies are opt-in and canonical symlink targets must remain contained. Direct results are stable-sorted within the entries encountered before the scan bound, while bounded enumeration membership is filesystem-defined. `RecursiveAttachmentCompletionProvider` adds opt-in iterative search with independent depth, visited-entry, result, containment, and cancellation bounds. Fuzzy ranking is opt-in via `AutocompleteFuzzyRanking.Enabled`; refresh debounce is injectable via `EditorAutocompleteDebouncer`, and stale async results are ignored through cancellable request handles.
- Markdown is separated into a pluggable module instead of being part of the core module. The baseline renderer is dependency-free and intentionally smaller than `pi-tui`'s `marked`-based renderer. It supports theme hooks, readable fallback links, OSC 8 links when configured with hyperlink-capable terminal settings, optional fenced-code highlighter hooks, parser adapters behind the `MarkdownParser` boundary, normalized list markers by default, and opt-in source list marker preservation through `MarkdownRenderOptions(preserveSourceListMarkers = true)`. Task-list markers remain visible text, loose lists preserve baseline-supported blank-line separation, and nested or fully CommonMark-compliant list parsing remains deferred to optional parser adapters. Richer parser dependencies remain optional and require approval.
- Image protocol helpers are dependency-free in core, while the `Image` component lives in the optional `image` module. Shared JVM/Native `scalatui.terminal.Base64ImagePayload` validates standard base64 before output and `scalatui.terminal.Base64ImagePayloadError` reports typed failure. Empty, padded, and decoder-valid unpadded values with modulo-four remainder zero, two, or three are accepted unchanged; remainder one is rejected. Validation transiently allocates and discards decoded bytes and does not validate image format. The image module provides `scalatui.image.Image.fromBase64`, plus file/byte helpers that return validated payloads, MIME type, and PNG/JPEG/GIF/WebP dimensions. Protocol helpers and `Image` no longer accept raw strings, and `ImageSource.payload` replaces `base64Data`; this is a source-breaking migration with no compatibility or deprecation path. iTerm2 filenames are standard-base64 encoded from UTF-8, absent names are omitted, and fallback filename/MIME controls become visible escaped text before width truncation. Scaling and transcoding remain deferred to optional dependency-backed modules.
- `Component.render(width)` and `ComponentFrameBuilder.result()` return `ComponentRender` on JVM and Scala Native. Text-only implementations migrate directly from `Vector[String]` to `ComponentRender.text`; there is no overload, implicit conversion, adapter, or deprecation path. `ComponentRender` requires explicit `lines`, `controls`, and `cursorPlacements` fields. This structured cursor migration is another direct source break with no default field or compatibility path. Frame-relative `CursorPlacement` values use zero-based display-cell coordinates and encode no terminal bytes. A cursor must identify a returned row and use a column below the requested width. A control footprint must fit within the returned rows and requested display width. Invalid surviving geometry fails before terminal output instead of being moved, dropped, partially encoded, or converted to text. `ComponentFrameBuilder.startRow` and `startCol` notify `RenderOriginAware` components only; returned controls and cursors remain frame-local and are rebased by locally accumulated rows. Boxes add body and normalized padding offsets. Higher opaque half-open overlay rectangles remove covered lower cursor candidates before appending translated higher candidates.
- siglyph intentionally does not port `pi-tui` string-prefix image detection. Image-looking bytes in an ordinary line gain no semantic control authority or image-specific rendering behavior. Supported image authority comes only from closed, read-only `TerminalRenderControl` values created by typed `TerminalImageProtocol` helpers. The TUI retains semantic controls through composition and differential state and encodes them only while assembling final synchronized output. Fullscreen clipping omits fully hidden controls and any partially visible Kitty or iTerm2 control because cell geometry cannot validate source-pixel cropping. Per-TUI Kitty retention reuses unchanged uploaded data within count and generation-age bounds. No arbitrary trusted-string API or raw-string protocol alternative exists. Direct backend writes remain explicit application authority outside the component-output trust boundary.
- Normal-screen applications can commit a detached typed component to scrollback with
  `TUI.appendToScrollback` when `PreserveScrollback` is enabled. The callback-completed operation is
  bounded, keeps control encoding private, remaps appended Kitty IDs, and rejects retained iTerm2
  relocation rather than exposing a raw protocol escape path.
- Normal-screen resize recovery is a siglyph extension beyond current `pi-tui`. An opt-in
  `NormalResizeRecoveryProvider` can reconstruct an application-owned, current-width newest durable
  tail before the retained live frame when `PreserveScrollback` clears the active viewport. The
  provider is synchronous, retryable, text-only, and strictly bounded by both previous and current
  live-frame viewport capacity; its context exposes prior geometry for semantic tail selection.
  siglyph retains no transcript and makes no terminal-independent survivor or deduplication claim.
- Kitty retransmission differs from the earlier lowercase targeted cleanup: every old positive ID retransmitted by `a=T` is deleted first with one uppercase `a=d,d=I,i=<id>` command, and removed old IDs receive the same cleanup. Cleanup order follows previous-frame control order. New IDs and unchanged IDs outside a partial redraw range receive no cleanup. Delete-all remains `a=d,d=A`.
- Typed validation failures and `TerminalRenderControl.toString` retain only bounded semantic kind, optional ID, geometry, frame dimensions, and duplicate coordinates. They do not retain or print image payloads, filenames, controls, placements, or application text.
- Terminal runtime now sends a cell-size query (`ESC[16t`) on start and consumes valid terminal replies (`ESC[6;H;Wt`) for runtime image sizing. `ImageRenderOptions` uses fixed `ImageCellDimensions` by default; low-level protocol helpers opt into queried dimensions with `ImageCellDimensionsSource.Runtime`. When reply data is missing or invalid, runtime image layout keeps deterministic fallback dimensions (`9x18`).
- The high-level `Image` component defaults to runtime cell dimensions because it renders inside a `TUI` lifecycle that owns the cell-size query. Pass `ImageRenderOptions(cellDimensionsSource = ImageCellDimensionsSource.Fixed, cellDimensions = ...)` when component rendering must use deterministic caller-supplied dimensions.
- Image rendering keeps cursor-safe layout by reserving protocol-reported rows before subsequent content in component output, including Kitty and iTerm2 protocols.
- JVM raw mode initially uses `stty` rather than JLine; JLine would require explicit dependency approval.
- Resize hardening keeps component width contracts testable and sanitizes final over-wide output so narrow resizes do not stop the session. Normal-screen resize clears viewport and scrollback by default. `NormalResizeClearPolicy.PreserveScrollback` is explicit. Both alternate-screen paths clear only the alternate viewport.
- Overlay positioning follows `pi-tui`'s hybrid model: width and max-height resolve against current terminal dimensions; absolute row/column values win over percentages, percentages win over anchors, offsets are applied, and margins clamp final placement.
- `TruncatedText` mirrors `pi-tui`'s first-line-only, ANSI-aware truncating status/header widget, while keeping output width-safe for narrow terminal rendering.
- `SettingsList` ports the core settings-row behavior with typed `TerminalInput`, value cycling, descriptions, scroll indicators, cancellation callbacks, optional containment filtering, optional fuzzy ranking, and application-provided submenus through existing overlay contracts.
- `Loader` and `CancellableLoader` are available as shared-core, tick-driven components. They port the visible indicator/message rendering, message and indicator mutation, idempotent lifecycle, Escape cancellation, and cancellation state, but intentionally do not own a Node-style `setInterval` timer.
- `CancellableLoader` exposes a Scala `CancellationToken` plus `cancel()`/`onCancel` instead of JavaScript `AbortSignal`; applications adapt that token to their own async/effect runtime when needed.
- Advanced keyboard support stays typed. `TerminalInput.KeyEvent` models press, repeat, and release metadata while the two-argument `TerminalInput.Key` constructor and pattern remain press-compatible for existing components. Release events are routed only to components that opt in with `wantsKeyRelease`.
- Coordinate-aware mouse input corresponds to the pinned upstream fullscreen mouse surface but uses typed contracts. Legacy `TUIOptions(mouseInput = true)` selects basic xterm tracking. `mouseTracking` can request drag or all-motion tracking. The runtime derives semantic movement, click, multi-click, drag, release, and wheel events against committed geometry. `MouseRegion` callbacks return typed render, capture, and focus intent.
- `ScrollView` corresponds to the pinned upstream one-child vertical viewport. Siglyph supports bounded offsets, follow-end, nested chain or contain overscroll, proportional scrollbars, prompt navigation, and jump-to-end. Automatic scrollbars remain visible while content overflows instead of using a Node timer to hide after activity.
- Fullscreen search indexes normalized rendered primary-scroll-view text by content revision and width. It captures focused input, reveals the current match, and paints only visible match highlights.
- Fullscreen selection stores bounded normalized plain-text grapheme offsets and copies only through the explicit `ClipboardTarget.Host` callback. Edge scrolling advances at most one row per drag motion event instead of using an application-hidden timer. Core reports `Success`, `Unsupported`, or `Failure`. OSC 52 is unsupported for this change and no selection operation emits terminal clipboard sequences.
- JVM and Native backends expose conservative Kitty keyboard protocol negotiation hooks. The current implementation does not add unsafe platform modifier probing; when modified Enter or similar platform state cannot be queried safely, the parser emits the ordinary key event.
- Terminal title and progress are optional backend capabilities rather than required `Terminal` methods. `TUI.setTerminalTitle` and `TUI.setTerminalProgress` return `false` on unsupported terminals such as `StreamTerminal` and emit no unsupported escape sequences.
- Terminal input draining is an optional backend capability rather than a required `Terminal` method. `TUI.stop()` invokes it when a backend supports bounded draining and skips it for unsupported backends.
- Insert-key input now has the typed `TerminalKey.Insert` identity instead of requiring `TerminalKey.Unknown("insert")` for supported standard and modified Insert sequences.
- Terminal background color and color-scheme queries are owned by `TUI`, not individual backends. Backends write query bytes and deliver raw replies. `TUI` exposes dependency-free callbacks with `Success`, `InvalidResponse`, `Stopped`, and `Failed` results, uses one wire flight per protocol, preserves subscription order, and returns an independently cancellable idempotent silent function. Applications own timeout scheduling; core has no query timer, executor, `Future`, effect type, compatibility overload, or optional effect module.
- Runtime input, callbacks, rendering, retained title/progress output, and cleanup progression use one
  synchronous owner drain. The short lifecycle lock only publishes state; application code,
  dimensions, query waits, and terminal operations run outside it. A separate non-nested backend
  edge lock serializes TUI-owned output.
- Resize attempts snapshot generation and dimensions and reject candidates known to be stale before
  output or differential-baseline mutation. Normal-screen width or height redraw clears normal
  scrollback; alternate-screen redraw clears only the alternate viewport.
- Callback color queries reserve direct request emission and correlate replies under short lifecycle
  state without invoking application code on ingress. Completion batches share accepted FIFO order
  with ordinary input and notifications, while the drain claims and invokes subscribers outside
  runtime locks. Stop discards queued ordinary work but retains accepted query completions required
  before cleanup.
- Ordinary terminal ingress is lossless and bounded at 4096 events. Correlation-only raw fragments
  consume no slot, a recognized protocol callback/notification batch consumes one slot, and each
  reconstructed ordinary raw event consumes one slot. A full queue backpressures output-producing
  publication until dequeue or lifecycle rejection; resize is coalesced without a queue slot.
- Editor bracketed paste remains byte-streamed through parser and runtime transit but is one logical
  edit. Newline normalization, grapheme and line marker thresholds, cursor placement, undo,
  `onChange`, autocomplete refresh, and rendering apply once across the whole stream.
- `Terminal.start` does not invoke registered callbacks synchronously on its call stack. Backends may
  publish independently from another thread before it returns. This strengthened contract replaces
  startup callback replay accommodation and complements output-side callback separation.
- The three-callback `Terminal.start` overload is a Siglyph lifecycle extension. Built-in input,
  fragment-flush, and resize workers report unexpected active-generation failures at most once per
  worker. EOF, stop interruption, and stale generations remain normal outcomes. `TUI.run()` wakes,
  restores terminal state, and rethrows the first failure with independent later failures suppressed.
