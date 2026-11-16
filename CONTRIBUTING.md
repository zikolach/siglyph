# Contributing to siglyph

Thanks for your interest in contributing. siglyph is a Scala 3 terminal UI
library inspired by pi-tui, with shared JVM/Scala Native core code and a small
set of optional modules.

## Development setup

Requirements:

- JDK 21
- Mill 1.3.1 (pinned in the build header)
- Scala 3.9.0 LTS (resolved by Mill)
- [Native toolchain and system libraries](docs/getting-started.md#scala-native) for all-module compile/test commands
- `openspec` for spec/change validation when working on user-visible behavior

The build header also selects Temurin JDK 21 for Mill and its compile/test/run tasks.
See [toolchain compatibility](docs/getting-started.md#toolchain-compatibility) for the consumer baseline.

Optional direnv configuration belongs in your local `.envrc`; it and `.direnv/` are ignored.
Do not commit credentials or machine-specific environment configuration. The tracked build header
remains the shared Mill/JDK version source.

## Validation

For library code changes, run:

```bash
mill __.compile
mill __.test
mill scalafmtCheck
mill scalafixCheck
openspec validate --all --strict
```

### Focused tests

Focused tests are usually faster while iterating:

```bash
mill core.test
mill core.test.testOnly scalatui.components.EditorSuite
mill interactiveDemo.test
```

### Documentation checks

Python 3.9+ is sufficient for the offline checks (no added dependencies or GitHub Actions budget):

```bash
python3 scripts/check-docs.py
python3 -m unittest discover -s scripts -p 'test_check_docs.py'
```

When changing examples, also compile the canonical JVM/Native quickstarts and marked guide snippets against published artifacts (requires Scala CLI, JDK 21 and dependency downloads):

```bash
python3 scripts/check-docs.py --compile
```

The [checker](scripts/check-docs.py) and its [regression tests](scripts/test_check_docs.py) validate local Markdown targets/anchors, primary installation versions and placeholders, and the README's canonical example. Keep the README block synchronized with `examples/scala-cli/hello.scala`; mark additional standalone Scala recipes with `<!-- compile-example -->` before their code fence. Compilation does not verify real-terminal appearance; use the [interactive smoke checklist](docs/interactive-smoke.md) when behavior changes. Historical release records are not forced to the latest version.

For public API changes, run the [compatibility gate](docs/api-stability.md) against a locally staged candidate. `python3 scripts/check-compatibility.py --inventory-only` checks source inventory drift without fetching artifacts.

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

Use the [validation checklist](#validation) appropriate to your change. Record commands and any verification gaps in the PR description. Documentation-only changes do not require rerunning the full library suite.

## License

By contributing, you agree that your contributions are licensed under the MIT
License in `LICENSE`.
