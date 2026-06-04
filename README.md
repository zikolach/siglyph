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

Editor, autocomplete, images, and Markdown are planned after the first milestone. Markdown will live in a separate pluggable module so parser dependencies can be evaluated explicitly for JVM and Native.

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

The interactive demo controls are:

- `Tab` switches focus between actions and input
- `↑` / `↓` move through actions when the action list is focused
- `Enter` submits the input or selects the focused action
- `Ctrl+L` clears messages
- `Esc` or `Ctrl+C` exits and restores terminal state

Run the JVM key tester:

```bash
mill keyTester.run
```

## Development

```bash
mill core.test
```

Run all currently wired checks:

```bash
mill __.compile
mill core.test
openspec validate --all --strict
```
