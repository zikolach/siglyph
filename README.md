# scala-tui

`scala-tui` is a Scala 3 terminal UI library inspired by [`pi-tui`](https://github.com/earendil-works/pi/tree/main/packages/tui). The goal is to provide a Node-free, dependency-light TUI for Scala applications while preserving the important behavior of `pi-tui`: component rendering, differential terminal output, typed input, Unicode-aware text handling, and testable terminal backends.

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

Autocomplete, images, and richer Markdown are planned after the first milestone. The multiline editor now starts with a pure `EditorBuffer` model plus a rendered `Editor` component in `core`, keeping text mutation and visual layout testable. Markdown will live in a separate pluggable module so parser dependencies can be evaluated explicitly for JVM and Native.

## Demos

Render a non-interactive frame using the stream backend:

```bash
mill demo.run
```

Run the JVM interactive demo in a macOS/Linux TTY:

```bash
mill interactiveJvmDemo.run
```

Build the Scala Native interactive demo:

```bash
mill interactiveNativeDemo.nativeLink
```

The interactive demo now showcases the multiline editor. Controls are:

- `Tab` switches focus between actions and editor
- `↑` / `↓` move through actions when the action list is focused
- `Enter` submits editor text or selects the focused action
- `Shift+Enter` inserts a newline in the editor when the terminal reports a normalized modified Enter event
- `Ctrl+A` / `Ctrl+E`, arrows, `Home` / `End`, `Backspace`, `Delete`, `Ctrl+K`, and `Ctrl+W` edit the multiline buffer
- `Ctrl+L` clears submitted messages
- `Esc` or `Ctrl+C` exits and restores terminal state

Run the JVM key tester:

```bash
mill keyTester.run
```

## Multiline editor API

`scalatui.components.Editor` is a rendered multiline component backed by `scalatui.editing.EditorBuffer`. It exposes `onChange` and `onSubmit` callbacks, focus-aware fake cursor rendering, Unicode/grapheme-aware edits, and configurable Enter behavior via `EditorEnterBehavior`.

Default prompt-like behavior submits on `Enter` and inserts a newline on `Shift+Enter`. Editor-like behavior can be configured with `EditorEnterBehavior.NewlineOnEnter()`, where plain `Enter` inserts a newline and `Cmd/Super+Enter` submits. Modified Enter support depends on terminal/parser normalization.

The first editor component intentionally defers autocomplete, overlays, undo/kill-ring, large-paste marker compaction, IME cursor markers, and hardware cursor positioning.

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
