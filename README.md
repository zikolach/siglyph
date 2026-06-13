# siglyph

`siglyph` is a Scala 3 terminal UI library inspired by [`pi-tui`](https://github.com/earendil-works/pi/tree/main/packages/tui). The goal is to provide a Node-free, dependency-light TUI for Scala applications while preserving the important behavior of `pi-tui`: component rendering, differential terminal output, typed input, Unicode-aware text handling, and testable terminal backends.

## Initial scope

- Build tool: latest stable Mill.
- Language: Scala 3.
- Initial package namespace: `scalatui`.
- Platforms for v0: macOS and Linux.
- Backends: Scala Native POSIX backend and JVM Unix backend behind the same terminal abstraction.
- JVM raw mode: initially via `stty`; it should fail clearly when raw-mode startup is requested and `stty` is unavailable.
- Windows and Scala.js/browser support are out of scope for v0.

## First milestone

The first usable milestone targets:

- Core component API.
- `Component`, `Focusable`, and `Container`.
- Virtual terminal test backend.
- Core renderer foundation.
- MVP components: `Text`, `Box`, `Spacer`, `SelectList`, and `Input`.

The multiline editor now starts with a pure `EditorBuffer` model plus a rendered `Editor` component in `core`, keeping text mutation and visual layout testable. The core runtime also includes a generic overlay stack, editor autocomplete contracts for dependency-free suggestions, image protocol capability helpers, and optional cursor markers for IME/hardware-cursor workflows. Markdown lives in a separate pluggable module with a dependency-free baseline renderer so parser dependencies can still be evaluated explicitly for richer JVM and Native adapters.

## Demos

Render a non-interactive static component showcase using the stream backend. It includes `TruncatedText` and `SettingsList` alongside the original MVP widgets:

```bash
mill demo.run
```

Run the JVM interactive demo in a macOS/Linux TTY:

```bash
mill interactiveJvmDemo.run
# Optional: enable hardware cursor positioning for manual validation
mill interactiveJvmDemo.run -- --hardware-cursor
```

Build the Scala Native interactive demo:

```bash
mill interactiveNativeDemo.nativeLink
```

From the output binary path (example):

```bash
out/interactiveNativeDemo/nativeLink.dest/out --hardware-cursor
```

The interactive demo showcases the multiline editor, overlay-backed slash-command autocomplete, tick-driven loaders, cancellable loaders, and resize-safe rendering. Controls include:

- `Ctrl+T` switches focus between actions and editor
- `↑` / `↓` move through actions when the action list is focused
- `Enter` submits editor text, selects the focused action, ticks/cancels loaders through actions, or accepts the selected autocomplete suggestion
- `Shift+Enter` inserts a newline in the editor when the terminal reports a normalized modified Enter event
- Type `/`, `./`, or `@` in the editor and press `Tab` to autocomplete; `↑` / `↓` navigate suggestions, `Enter` or `Tab` accepts, and `Esc` cancels
- `PageUp` / `PageDown` page within wrapped multiline text; `Ctrl+]` and `Ctrl+Alt+]` jump among typed target characters
- `Ctrl+A` / `Ctrl+E`, arrows, `Home` / `End`, `Backspace`, `Delete`, `Ctrl+K`, and `Ctrl+W` edit the multiline buffer
- `Ctrl+-` undoes the previous edit, `Ctrl+Y` yanks the latest killed text, `Alt+Y` yank-pops, `Alt+D` / `Alt+Delete` deletes a word forward, and `Alt+Left` / `Alt+Right` or `Ctrl+Left` / `Ctrl+Right` move by word where the terminal reports those modifiers
- `Ctrl+L` clears submitted messages
- `Esc` or `Ctrl+C` exits and restores terminal state
- Editor keybindings are configurable via `EditorOptions.keybindings` (`KeybindingManager`) and keep defaults unless overridden.

Run the JVM key tester:

```bash
mill keyTester.run
```

## Multiline editor API

`scalatui.components.Editor` is a rendered multiline component backed by `scalatui.editing.EditorBuffer`. It exposes `onChange` and `onSubmit` callbacks, focus-aware fake cursor rendering with a zero-width `CursorMarker`, Unicode/grapheme-aware edits, pi-tui-style undo/kill-ring behavior, large-paste marker compaction/expansion, and configurable Enter behavior via `EditorEnterBehavior`.

By default the runtime preserves existing fake-cursor-only behavior while stripping cursor markers before terminal output. Applications that want terminal-native cursor/IME positioning can opt in with `TUIOptions(hardwareCursorPositioning = true)` when constructing `TUI`; the shared renderer scans the final composited frame, strips marker metadata, and moves the hardware cursor to the focused editor/input cursor without replacing the visible fake cursor.

Default prompt-like behavior submits on `Enter` and inserts a newline on `Shift+Enter`. Editor-like behavior can be configured with `EditorEnterBehavior.NewlineOnEnter()`, where plain `Enter` inserts a newline and `Cmd/Super+Enter` submits. Modified Enter support depends on terminal/parser normalization.

The editor can be configured with `scalatui.autocomplete.AutocompleteProvider` implementations. Suggestions are displayed through the generic TUI overlay stack and default to editor-adjacent placement, so applications do not need to compute terminal rows for normal editor autocomplete. Provider lookups use a cancellable callback boundary so applications can bridge file, network, `Future`, or other effect runtimes without adding dependencies to `siglyph` itself. Slash-command helpers are metadata providers only; applications remain responsible for interpreting submitted commands.

`CombinedAutocompleteProvider` composes slash-command suggestions with a dependency-free path/attachment completion source. It parses slash prefixes, quoted paths, `@` attachment markers, delimiter-aware replacements, and force-refresh requests (`Tab`) deterministically while preserving cancellation/stale-response safety in the editor.

Large bracketed pastes are compacted to markers such as `[paste #1 +11 lines]` in the rendered buffer. `onSubmit` receives expanded logical text, and applications can call `expandPasteMarkers()` to replace markers in the visible buffer before submission.

## Markdown and images

`markdown` provides `MarkdownParser`, `MarkdownRenderer`, `BasicMarkdownRenderer`, and a `Markdown` component. The baseline renderer is dependency-free and supports a conservative readable subset: headings, paragraphs, inline emphasis normalization, inline code, links, fenced/indented code, lists, block quotes, horizontal rules, and simple pipe tables. Richer parser integrations should live in optional adapter modules.

`core` exposes dependency-free image protocol helpers under `scalatui.terminal.TerminalImageProtocol` plus `ImageDimensions`, `ImageRenderOptions`, and related types. The optional `image` module provides `scalatui.image.Image`, which emits Kitty/iTerm2 escapes when `TerminalCapabilities.images` is available and renders a readable fallback otherwise. Image file loading, dimension sniffing, scaling, or transcoding are intentionally left to future optional helpers or application code.

## Utility components

`scalatui.components.TruncatedText` renders a single logical line for status/header text. It keeps only the text before the first newline, truncates by ANSI-aware visible terminal width, supports horizontal and vertical padding, and returns width-safe lines for JVM and Native.

`scalatui.components.SettingsList` renders interactive configuration rows with stable ids, labels, current values, optional descriptions, optional cycle values, hints, and scroll indicators. Controls are:

- `↑` / `↓` move selection
- `Enter` or `Space` cycles the selected row when cycle values are configured
- `Esc` invokes the cancel callback
- When dependency-free filtering is enabled, printable characters append to the search query and `Backspace` edits it; matching uses case-insensitive containment across id, label, value, and description

`scalatui.components.Loader` renders a width-safe indicator plus message for long-running work. It is deliberately tick-driven in shared core: call `start()` to mark it running, call `tick()` from application-owned scheduling to advance frames, use `setMessage(...)` / `setIndicator(...)` to update display state, and call `stop()` when work completes. State changes request renders automatically when the loader is attached to a `TUI`, but no background thread, timer, or effect runtime is created by the component.

`scalatui.components.CancellableLoader` extends loader behavior with dependency-free cancellation. `Esc` or explicit `cancel()` marks its `CancellationToken` as cancelled and invokes `onCancel` at most once. This intentionally differs from `pi-tui`'s Node `setInterval` and JavaScript `AbortSignal`; automatic scheduler integration remains a possible follow-up.

## Resize and narrow terminals

Interactive runtimes clamp render dimensions to positive sizes, redraw fully when terminal width or height changes, and sanitize final over-wide output before writing to the terminal. Components should still obey the render-width contract in tests, but the runtime protects live terminal sessions from crashing when a component or demo line is too wide during resize.

JVM and Scala Native interactive backends poll terminal dimensions while running and request redraws when size changes, without adding runtime dependencies.

## Development

Import project to IntelliJ IDEA:
```bash
mill mill.idea/
```

```bash
mill core.test
```

Run all currently wired checks:

```bash
mill __.compile
mill core.test
mill quality
openspec validate --all --strict
```

Formatting and linting:

```bash
mill scalafmtAll     # format sources using .scalafmt.conf
mill scalafmtCheck   # check formatting
mill scalafixCheck   # run baseline syntactic Scalafix rules
mill quality         # run formatting and Scalafix checks
```

Public APIs should include Scaladoc, and user-visible behavior should be reflected in README/docs or the relevant OpenSpec artifacts.
