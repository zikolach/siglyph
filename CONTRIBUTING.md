# Contributing to siglyph

Thanks for your interest in contributing. siglyph is a Scala 3 terminal UI
library inspired by pi-tui, with shared JVM/Scala Native core code and a small
set of optional modules.

## Development setup

Requirements:

- JDK 21
- Mill 1.1.6 or newer compatible Mill 1.x
- `openspec` for spec/change validation when working on user-visible behavior

Useful commands:

```bash
mill __.compile
mill __.test
mill scalafmtCheck
mill scalafixCheck
openspec validate --all --strict
```

Focused tests are usually faster while iterating:

```bash
mill core.test
mill core.test.testOnly scalatui.components.EditorSuite
mill interactiveDemo.test
```

## Contribution workflow

1. Open an issue or discussion first for substantial API or behavior changes.
2. Use the OpenSpec workflow for new user-visible behavior.
3. Keep public APIs documented with Scaladoc.
4. Add or update tests for behavior changes.
5. Update README/docs when controls, commands, package names, or public APIs change.
6. Keep JVM- and Scala Native-specific code behind shared abstractions where possible.

## Style notes

- Scala 3 only; prefer the braceless style already used in the repository.
- Prefer dependency-light implementations. Do not add runtime dependencies without prior discussion.
- Prefer `scalatui.syntax.Equality.*` and `===` / `!==` for intentional equality checks.
- Prefer `scalatui.syntax.Containment.*` and `contains_` when collection membership should be element-type checked.
- Tests should use `VirtualTerminal` or stream fakes instead of real terminals when possible.

## Pull requests

Before opening a pull request, run at least:

```bash
mill __.compile
mill __.test
```

If the change affects formatting, linting, or OpenSpec artifacts, also run:

```bash
mill scalafmtCheck
mill scalafixCheck
openspec validate --all --strict
```

## License

By contributing, you agree that your contributions are licensed under the MIT
License in `LICENSE`.
