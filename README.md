# siglyph

[![CI](https://github.com/zikolach/siglyph/actions/workflows/ci.yml/badge.svg)](https://github.com/zikolach/siglyph/actions/workflows/ci.yml)

**siglyph** is a Scala 3 terminal UI library inspired by [`pi-tui`](https://github.com/earendil-works/pi/tree/main/packages/tui). It provides dependency-light components, typed terminal input, Unicode-aware text editing, differential rendering, overlays, and JVM/Scala Native terminal backends.

The public Scala package namespace is `scalatui`.

## Status

siglyph is pre-1.0. The current focus is a small, testable TUI core with `pi-tui`-style editing and input behavior. macOS and Linux are the target platforms; Windows and Scala.js/browser support are out of scope for now.

## Install

Published artifacts are currently on GitHub Packages. If the repository/package is private, consumers need GitHub Packages credentials with `read:packages`.

### SBT

```scala
resolvers += "GitHub Packages: siglyph" at "https://maven.pkg.github.com/zikolach/siglyph"

libraryDependencies ++= Seq(
  "io.github.zikolach" %% "siglyph-core" % "0.1.0",
  "io.github.zikolach" %% "siglyph-terminal-jvm" % "0.1.0",
  "io.github.zikolach" %% "siglyph-markdown" % "0.1.0",
  "io.github.zikolach" %% "siglyph-image" % "0.1.0"
)
```

### Mill

```scala
object app extends ScalaModule {
  def scalaVersion = "3.7.4"

  def repositories = Task {
    super.repositories() ++ Seq("https://maven.pkg.github.com/zikolach/siglyph")
  }

  def mvnDeps = Seq(
    mvn"io.github.zikolach::siglyph-core::0.1.0",
    mvn"io.github.zikolach::siglyph-terminal-jvm::0.1.0",
    mvn"io.github.zikolach::siglyph-markdown::0.1.0",
    mvn"io.github.zikolach::siglyph-image::0.1.0"
  )
}
```

Most applications start with `siglyph-core` plus one backend, usually `siglyph-terminal-jvm`.

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

A multiline editor with slash-command autocomplete:

```scala
import scalatui.autocomplete.*
import scalatui.components.*
import scalatui.core.TUI
import scalatui.terminal.jvm.SttyTerminal

@main def editorTui(): Unit =
  val tui = TUI(SttyTerminal())
  val output = Text("Submitted: (none)")
  val editor = Editor(options = EditorOptions(
    autocompleteProvider = Some(SlashCommandAutocompleteProvider(Vector(
      SlashCommand("help", Some("Show help")),
      SlashCommand("quit", Some("Exit"))
    ))),
    onSubmit = text => output.text = s"Submitted: $text"
  ))

  tui.addChild(output)
  tui.addChild(editor)
  tui.setFocus(editor)
  tui.run()
```

## Demos

The demos are the best starting point for real usage:

| Command | Source | What it shows |
| --- | --- | --- |
| `mill demo.run` | [`demo/src/scalatui/demo/MvpDemo.scala`](demo/src/scalatui/demo/MvpDemo.scala) | non-interactive rendering through `StreamTerminal` |
| `mill interactiveJvmDemo.run` | [`interactiveJvmDemo/src/scalatui/demo/InteractiveJvmDemo.scala`](interactiveJvmDemo/src/scalatui/demo/InteractiveJvmDemo.scala) + [`interactiveDemo/src/scalatui/demo/InteractiveDemo.scala`](interactiveDemo/src/scalatui/demo/InteractiveDemo.scala) | interactive JVM app, editor, autocomplete, file-manager mode, loaders, resize-safe rendering |
| `mill interactiveNativeDemo.nativeLink` | [`interactiveNativeDemo/src/scalatui/demo/InteractiveNativeDemo.scala`](interactiveNativeDemo/src/scalatui/demo/InteractiveNativeDemo.scala) | Scala Native launcher for the shared interactive demo |
| `mill keyTester.run` | [`keyTester/src/scalatui/demo/KeyTester.scala`](keyTester/src/scalatui/demo/KeyTester.scala) | typed terminal key/input inspection |

Interactive demo controls are also summarized in [`docs/interactive-smoke.md`](docs/interactive-smoke.md). Default keybindings are listed in [`docs/keybinding-defaults.md`](docs/keybinding-defaults.md).

## Features

- **Rendering core:** `Component`, `Focusable`, `Container`, differential terminal output, overlays, virtual terminal tests.
- **Components:** `Text`, `Box`, `Spacer`, `Input`, `Editor`, `SelectList`, `SettingsList`, `Loader`, `CancellableLoader`.
- **Editing:** Unicode/grapheme-aware movement and deletion, large-paste compaction, prompt history, undo, kill-ring, yank/yank-pop, page movement, jump-to-character.
- **Keybindings:** shared `KeybindingManager` with configurable editor/input/select command bindings.
- **Autocomplete:** slash commands and dependency-free path/attachment completion contracts.
- **Terminals:** JVM `stty` backend, Scala Native POSIX backend, stream and virtual test backends.
- **Optional modules:** dependency-free Markdown rendering and terminal image protocol helpers.

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
