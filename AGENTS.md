# Working on scala-tui

Internals and conventions for hacking on the library itself, for both
human contributors and AI assistants. End-user documentation lives in
the [README](README.md).

`scala-tui` is a Scala 3 terminal UI library inspired by
[`pi-tui`](https://github.com/earendil-works/pi/tree/main/packages/tui). The
project aims to provide a Node-free, dependency-light TUI for Scala
applications on the JVM and Scala Native, preserving important `pi-tui`
behavior: component rendering, differential terminal output, typed input,
Unicode-aware text handling, and testable terminal backends.

Use OpenSpec for feature work. Active and archived change artifacts live under
[`openspec/`](openspec/); promoted specs live under [`openspec/specs/`](openspec/specs/).

## Project layout

```
scala-tui/
├── build.mill
├── core/                  # shared component/core/terminal/unicode/ANSI APIs + tests
├── coreNative/            # Scala Native module compiling shared core/src via Mill
├── terminalJvm/           # JVM Unix/stty terminal backend
├── terminalNative/        # Scala Native POSIX terminal backend
├── markdown/              # pluggable Markdown module shell
├── demo/                  # non-interactive stream-render smoke demo
├── keyTester/             # JVM interactive key tester
├── interactiveDemo/       # shared interactive demo UI/logic
├── interactiveDemoNative/ # Native module compiling interactiveDemo/src via Mill
├── interactiveJvmDemo/    # JVM interactive demo launcher
├── interactiveNativeDemo/ # Scala Native interactive demo launcher
├── scripts/               # generation scripts, including Unicode tables
├── docs/                  # porting notes, smoke instructions, research notes
└── openspec/              # specs and change proposals/tasks
```

Dependency graph:

```
core
  ├── terminalJvm           → core
  ├── terminalNative        → coreNative
  ├── markdown              → core
  ├── demo                  → core
  ├── keyTester             → terminalJvm
  ├── interactiveDemo       → core
  ├── interactiveDemoNative → coreNative
  ├── interactiveJvmDemo    → terminalJvm + interactiveDemo
  └── interactiveNativeDemo → terminalNative + interactiveDemoNative
```

The public package namespace is `scalatui`. Keep JVM- and Native-specific code
behind the shared terminal abstractions where possible. Shared JVM/Native sources
are canonical under `core/src` and `interactiveDemo/src`; Native mirror modules
reuse them through Mill source-root configuration rather than filesystem symlinks.

## Build and test

```bash
mill __.compile                         # build every module
mill core.test                          # run core unit tests
mill core.test.testOnly scalatui.core.TUISuite
mill scalafmtCheck                      # verify Scalafmt formatting
mill scalafixCheck                      # verify baseline Scalafix rules
openspec validate --all --strict        # validate specs and active changes
```

Build or run demos:

```bash
mill demo.run                           # non-interactive frame demo
mill keyTester.run                      # JVM interactive key tester
mill interactiveJvmDemo.run             # JVM interactive demo
mill interactiveNativeDemo.nativeLink   # build Native interactive demo
out/interactiveNativeDemo/nativeLink.dest/out
```

Interactive demo controls:

- `Ctrl+T` switches focus between actions and input
- `↑` / `↓` move through actions when the action list is focused
- `Enter` submits input or selects the focused action
- `Ctrl+L` clears messages
- `Esc` or `Ctrl+C` exits and restores terminal state

If an interactive TTY run leaves the shell in a bad state, run:

```bash
stty sane
```

## OpenSpec workflow

- Use `/opsx-propose` for new behavior and `/opsx-apply <change>` to implement
  an approved change.
- Before implementing, read the change's proposal, design, tasks, and affected
  spec deltas.
- Mark tasks complete in the change `tasks.md` as work lands.
- Validate with `openspec validate --all --strict` before reporting completion.
- Archive completed changes with the archive workflow after implementation and
  validation are done.

## Conventions

### Scala style

- Scala 3 only; prefer braceless syntax used throughout the repo.
- Explicit return types on public members.
- Keep APIs Scala-idiomatic and typed; document `pi-tui` counterparts where it
  clarifies parity.
- Keep platform-specific behavior in compatibility layers or backend modules.
- Prefer `scalatui.syntax.Equality.*` and `===` / `!==` for intentional equality
  comparisons so strict Scalafix can reject universal `==` / `!=`.
- Prefer `scalatui.syntax.Containment.*` and `contains_` for collection
  membership when the searched value should be constrained to the element type.
- Public APIs should include Scaladoc that explains contract, platform scope,
  and important non-goals.
- User-visible behavior changes should update README/docs or relevant OpenSpec
  artifacts. Future OpenSpec tasks should include documentation and Scaladoc
  work when public APIs are added or changed.
- Tests should target one behavior each and use `VirtualTerminal`/stream fakes
  instead of real terminals when possible.

### Terminal/runtime behavior

- Components receive typed `TerminalInput` where sensible.
- Raw terminal reads can fragment escape sequences; feed reads through
  `TerminalInputBuffer` before dispatching parsed input.
- Control-key normalization belongs in the parser layer.
- Terminal backend `stop()` should be idempotent and restore terminal state,
  including cursor visibility and bracketed paste mode.
- Interactive demos should share UI construction/logic and differ only by
  backend launcher.

### Dependencies

- Do not add runtime dependencies without explicit confirmation.
- Test-only dependencies are acceptable when they are scoped to tests.
- Markdown parser dependencies require research/approval and belong in the
  pluggable `markdown` module.
- Unicode tables are generated with Scala CLI and committed to the repo; keep
  generated data deterministic and note the Unicode version.

### Code generation

Unicode table generation lives in:

```bash
scripts/GenerateUnicodeTables.scala
```

Generated tables are committed under:

```bash
core/src/scalatui/unicode/UnicodeTables.scala
```

## Documentation

- README: end-user quick start and demo commands.
- `docs/porting-notes.md`: `pi-tui` parity/porting notes.
- `docs/interactive-smoke.md`: manual interactive smoke coverage.
- `docs/markdown-parser-research.md`: Markdown dependency research.
- `docs/post-mvp-plan.md`: roadmap notes.

When changing public behavior, update README/docs and the relevant OpenSpec
specs or change artifacts.
