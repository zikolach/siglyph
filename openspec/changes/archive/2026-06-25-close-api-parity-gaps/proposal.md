## Why

siglyph is close to `pi-tui` for core rendering, editing, autocomplete, terminal protocols, and components, but several small public API and behavior gaps still make application ports less direct. This change closes selected parity gaps that are useful for agent-style applications while preserving siglyph's typed input model and macOS/Linux platform scope.

## What Changes

- Add typed global input listeners so applications can observe or handle input before focused component routing without using raw terminal escape strings.
- Add a Scala-idiomatic editor programmatic insertion API when it is useful for porting `pi-tui` integrations such as agent UI helpers that currently use `insertTextAtCursor`.
- Add forced autocomplete behavior that can auto-apply a single unambiguous completion through an explicit option.
- Add an optional terminal input-drain capability for interactive backends that can flush pending terminal replies before shutdown.
- Add a first-class `TerminalKey.Insert` identity and parse insert-key sequences to that typed key.
- Update docs, Scaladoc, and porting notes for the new public APIs and intentional defaults.
- No new runtime dependencies.
- No Windows, browser, or Scala.js backend support.
- No raw-string input API replacement for siglyph's typed `TerminalInput` model.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `developer-api`: Adds public API expectations for typed global input listeners, editor programmatic insertion, and documentation requirements.
- `terminal-runtime`: Adds optional terminal drain capability behavior and `TerminalKey.Insert` parsing semantics.
- `autocomplete`: Adds explicit forced single-completion auto-apply behavior.
- `text-editing`: Adds editor programmatic insertion behavior and callback expectations.
- `keybinding-management`: Adds typed insert-key matching expectations where the keybinding model can represent the key.

## Impact

- Affected core APIs: `scalatui.core.TUI`, `scalatui.components.Editor`, `scalatui.terminal.TerminalInput`, `scalatui.terminal.TerminalKey`, `scalatui.terminal.TerminalInputParser`, and optional terminal capability types.
- Affected autocomplete APIs: editor autocomplete options and forced request handling.
- Affected backends: JVM `SttyTerminal`, Scala Native `PosixTerminal`, `StreamTerminal`, and `VirtualTerminal` only where optional drain or insert-key parsing requires integration.
- Affected tests: core TUI input routing tests, editor tests, autocomplete tests, terminal parser tests, keybinding tests, and backend lifecycle tests.
- Affected docs: README, `docs/porting-notes.md`, `docs/keybinding-defaults.md`, and Scaladoc for new public members.
