# siglyph

<p align="center">
  <img src="docs/assets/siglyph-logo.png" alt="siglyph logo" width="640">
</p>

[![CI](https://github.com/zikolach/siglyph/actions/workflows/ci.yml/badge.svg)](https://github.com/zikolach/siglyph/actions/workflows/ci.yml)

**siglyph** is a Scala 3 terminal UI library inspired by [`pi-tui`](https://github.com/earendil-works/pi/tree/main/packages/tui). It provides dependency-light components, typed terminal input, Unicode-aware text editing, differential rendering, overlays, and JVM/Scala Native terminal backends.

The public Scala package namespace is `scalatui`.

## Status

siglyph is pre-1.0. The current focus is a small, testable TUI core with `pi-tui`-style editing and input behavior. macOS and Linux are the target platforms; Windows and Scala.js/browser support are out of scope for now.

## Install

Published artifacts are available on Maven Central. GitHub Packages and GitHub Release jars are also published for releases; details live in [`docs/publishing.md`](docs/publishing.md).

### SBT

```scala
libraryDependencies ++= Seq(
  "io.github.zikolach" %% "siglyph-core" % "0.2.0",
  "io.github.zikolach" %% "siglyph-terminal-jvm" % "0.2.0",
  "io.github.zikolach" %% "siglyph-markdown" % "0.2.0",
  "io.github.zikolach" %% "siglyph-image" % "0.2.0"
)
```

### Mill

```scala
object app extends ScalaModule {
  def scalaVersion = "3.7.4"

  def mvnDeps = Seq(
    mvn"io.github.zikolach::siglyph-core::0.2.0",
    mvn"io.github.zikolach::siglyph-terminal-jvm::0.2.0",
    mvn"io.github.zikolach::siglyph-markdown::0.2.0",
    mvn"io.github.zikolach::siglyph-image::0.2.0"
  )
}
```

Most JVM applications start with `siglyph-core` plus `siglyph-terminal-jvm`. Scala Native applications use the same platform-aware `siglyph-core` coordinate plus `siglyph-terminal-native`; Mill resolves those to `_native0.5_3` artifacts from `ScalaNativeModule` projects.

## Try with Scala CLI

Single-file demos live in [`examples/scala-cli/`](examples/scala-cli/) and are intended for copy/paste or GitHub Gist use. For example:

```bash
./examples/scala-cli/markdown.scala
./examples/scala-cli/image.scala /path/to/image.png
```

They reference Maven Central dependencies, so they can run without cloning or GitHub Packages credentials.

## Quick example

A small JVM app with text, input, focus, and terminal lifecycle:

```scala
import scalatui.components.*
import scalatui.core.TUI
import scalatui.terminal.jvm.SttyTerminal

@main def helloTui(): Unit =
  val tui = TUI(SttyTerminal())
  val input = Input()
  input.onSubmit = value =>
    input.setValue("")
    tui.addChild(Text(s"You typed: $value"))

  tui.addChild(Text("siglyph demo — type and press Enter"))
  tui.addChild(input)
  tui.setFocus(input)
  tui.run()
```

A multiline editor with slash, filesystem, attachment, fuzzy, and `#` trigger autocomplete:

```scala
import java.io.File
import scalatui.autocomplete.*
import scalatui.components.*
import scalatui.core.TUI
import scalatui.terminal.jvm.SttyTerminal

@main def editorTui(): Unit =
  val tags = Vector("#bug", "#docs", "#demo")
  TriggerCompletionSource.fromPrefix(
    "#",
    query => Some(tags.filter(_.drop(1).startsWith(query)).map(tag =>
      AutocompleteItem(tag.drop(1), tag, Some("application-owned tag"))
    ))
  ) match
    case Left(error) =>
      System.err.println(s"Invalid autocomplete trigger: ${error.message}")
    case Right(tagSource) =>
      val tui = TUI(SttyTerminal())
      val provider = CombinedAutocompleteProvider(
        commands = Vector(SlashCommand("help"), SlashCommand("quit")),
        pathProvider = Some(FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(
          baseDirectory = File("."), // Java/NIO only; no fd/find/shell dependency
          maxResults = 20
        ))),
        triggerSources = Vector(tagSource),
        fuzzyRanking = AutocompleteFuzzyRanking.Enabled
      )
      val editor = Editor(options = EditorOptions(
        autocompleteProvider = Some(provider),
        // Debounce is explicit/injectable; pending requests are cancelled and late results ignored.
        autocompleteDebouncer = EditorAutocompleteDebouncer.Immediate,
        onSubmit = text => println(s"Submitted: $text")
      ))

      tui.addChild(Text("Try /he, ./, @\"README, or #do then Tab"))
      tui.addChild(editor)
      tui.setFocus(editor)
      tui.run()
```

## Markdown and image helpers

Markdown rendering stays in `siglyph-markdown`. The baseline renderer is dependency-free and supports theme hooks, readable link fallback, OSC 8 links when `TerminalCapabilities.hyperlinks` is true, parser adapters, and optional fenced-code highlighter hooks.

Image rendering stays in `siglyph-image`. `ImageSource.fromFile(path)` loads supported PNG, JPEG, GIF, and WebP files into base64 data, MIME type, and dimensions for the existing `Image` component contract. Unsupported terminals render readable fallback text. `examples/scala-cli/image.scala` is the quickest visual smoke test for protocol rendering, fallback behavior, runtime cell-size sizing in supported versions, and row reservation.

## Terminal integration helpers

`TUI` exposes optional terminal integration helpers for supported interactive backends:

```scala
val tui = TUI(SttyTerminal())
val unsubscribe = tui.onTerminalColorSchemeChange { scheme =>
  println(s"terminal scheme changed to ${scheme.value}")
}

tui.start()
try
  val titleApplied = tui.setTerminalTitle("siglyph")
  val progressApplied = tui.setTerminalProgress(active = true)
  val background = tui.queryTerminalBackgroundColor(timeoutMillis = 500)
  val scheme = tui.queryTerminalColorScheme(timeoutMillis = 500)
  tui.setTerminalColorSchemeNotifications(enabled = true)
finally
  unsubscribe()
  tui.stop()
```

Title and progress helpers return `false` when the backend does not support the protocol. Color queries and notifications require a started TUI so the backend can read and deliver terminal replies. `TUI` owns query correlation, timeouts, and protocol-reply interception before focused components receive input.

## Demos

The demos are the best starting point for real usage:

| Command | Source | What it shows |
| --- | --- | --- |
| `mill demo.run` | [`demo/src/scalatui/demo/MvpDemo.scala`](demo/src/scalatui/demo/MvpDemo.scala) | non-interactive rendering through `StreamTerminal` |
| `mill interactiveJvmDemo.run` | [`interactiveJvmDemo/src/scalatui/demo/InteractiveJvmDemo.scala`](interactiveJvmDemo/src/scalatui/demo/InteractiveJvmDemo.scala) + [`interactiveDemo/src/scalatui/demo/InteractiveDemo.scala`](interactiveDemo/src/scalatui/demo/InteractiveDemo.scala) | interactive JVM app, editor, autocomplete, rich SelectList/SettingsList behavior, file-manager mode, loaders, terminal integration helpers, resize-safe rendering |
| `mill interactiveNativeDemo.nativeLink && ./out/interactiveNativeDemo/nativeLink.dest/out` | [`interactiveNativeDemo/src/scalatui/demo/InteractiveNativeDemo.scala`](interactiveNativeDemo/src/scalatui/demo/InteractiveNativeDemo.scala) | Scala Native launcher for the shared interactive demo |
| `mill keyTester.run` | [`keyTester/src/scalatui/demo/KeyTester.scala`](keyTester/src/scalatui/demo/KeyTester.scala) | typed terminal key/input inspection |

For the Scala Native interactive demo, `nativeLink` builds the executable and the linked binary starts the app. Run both from an interactive terminal with `mill interactiveNativeDemo.nativeLink && ./out/interactiveNativeDemo/nativeLink.dest/out`. Optional flags go after the binary path, for example `mill interactiveNativeDemo.nativeLink && ./out/interactiveNativeDemo/nativeLink.dest/out --hardware-cursor`.

Interactive demo controls are also summarized in [`docs/interactive-smoke.md`](docs/interactive-smoke.md). Default keybindings are listed in [`docs/keybinding-defaults.md`](docs/keybinding-defaults.md).

## Features

- **Rendering core:** `Component`, `Focusable`, `Container`, differential terminal output, overlays, virtual terminal tests.
- **Components:** `Text`, `Box`, `Spacer`, `Input`, `Editor`, `SelectList`, `SettingsList`, `Loader`, `CancellableLoader`. `SelectList` and `SettingsList` support theme hooks, filtering, optional fuzzy ranking, and settings submenus through existing overlay contracts.
- **Editing:** Unicode/grapheme-aware movement and deletion, large-paste compaction, prompt history, undo, kill-ring, yank/yank-pop, page movement, jump-to-character.
- **Keybindings:** shared `KeybindingManager` with configurable editor/input/select command bindings. Typed key events can distinguish press, repeat, and release when terminals report that metadata.
- **Autocomplete:** slash commands, dependency-free filesystem path and `@` attachment completions, application-owned natural triggers such as `#`, optional fuzzy ranking, cancellable async providers, and injectable debounce scheduling.
- **Terminals:** JVM `stty` backend, Scala Native POSIX backend, stream and virtual test backends, optional title/progress helpers, runtime-owned background color and color-scheme queries, conservative Kitty keyboard protocol hooks, and readable fallback behavior when advanced metadata is unavailable.
- **Optional modules:** dependency-free Markdown rendering with theme/link/highlighter/parser hooks, plus terminal image protocol helpers with file loading, header dimension sniffing, and cell-size bounding helpers.

## Repository structure

```text
core/                    shared core APIs, components, editing, terminal abstractions, tests
terminalJvm/             JVM Unix/stty backend
terminalNative/          Scala Native POSIX backend
markdown/                Markdown parser/renderer module
image/                   Image component module
demo/                    non-interactive stream-render demo
interactiveDemo/         shared interactive demo UI/logic
interactiveJvmDemo/      JVM interactive demo launcher
interactiveNativeDemo/   Native interactive demo launcher
keyTester/               JVM terminal key tester
docs/                    usage notes, porting notes, smoke-test notes
openspec/                active and promoted OpenSpec change/spec artifacts
```

## Development

### Explicit BSP installation

```bash
mill --bsp-install
```

### IntelliJ IDEA XML Support

```bash
mill mill.idea/
```

### Project build and validation

```bash
mill __.compile
mill __.test
mill scalafmtCheck
mill scalafixCheck
openspec validate --all --strict
```

Useful focused commands:

```bash
mill core.test
mill core.test.testOnly scalatui.components.EditorSuite
mill interactiveDemo.test
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for contribution workflow and style notes.

## Attribution

siglyph is inspired by and partially ported from [`pi-tui`](https://github.com/earendil-works/pi/tree/main/packages/tui), part of the MIT-licensed `pi` project. See [NOTICE](NOTICE) for upstream attribution.

## License

siglyph is licensed under the [MIT License](LICENSE).
