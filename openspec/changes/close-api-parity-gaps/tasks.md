## 1. Typed Global Input Listeners

- [x] 1.1 Add a typed global input listener API to `TUI` with registration and removal support.
- [x] 1.2 Route terminal input through global listeners before focused component routing.
- [x] 1.3 Stop focused component routing when a listener returns a handled or exit result.
- [x] 1.4 Add tests for listener ordering, ignored listener routing, handled listener routing, render requests, exit requests, and listener removal.

## 2. Editor Programmatic Insertion

- [x] 2.1 Add a public editor insertion method that inserts text at the current cursor without requiring synthesized terminal input.
- [x] 2.2 Reuse existing editor mutation paths for newline normalization, large-paste markers, undo snapshots, change callbacks, autocomplete refresh, and render requests.
- [x] 2.3 Add editor tests for single-line insertion, multiline insertion, CRLF and CR normalization, one-step undo, callback behavior, render requests, and large-paste marker behavior.

## 3. Forced Autocomplete Auto-Apply

- [x] 3.1 Add an explicit editor autocomplete option for forced single-completion auto-apply with the default disabled.
- [x] 3.2 Apply exactly one forced suggestion through the existing provider completion contract when the option is enabled and the request snapshot is current.
- [x] 3.3 Preserve existing explicit-selection behavior for disabled auto-apply, multiple suggestions, empty suggestions, and stale responses.
- [x] 3.4 Add autocomplete tests for enabled single-result auto-apply, disabled auto-apply, multiple-result overlay behavior, empty-result behavior, and stale-result rejection.

## 4. Terminal Drain and Insert Key

- [x] 4.1 Add an optional terminal input-drain capability with bounded shutdown behavior.
- [x] 4.2 Integrate drain support into safe shutdown without widening the base `Terminal` trait.
- [x] 4.3 Add backend or virtual-terminal tests for supported drain, unsupported drain, bounded drain behavior, and idempotent stop behavior.
- [x] 4.4 Add `TerminalKey.Insert` and update input parsing for standard and supported modified Insert sequences.
- [x] 4.5 Add parser and keybinding tests for Insert and modified Insert matching.

## 5. Documentation and Validation

- [x] 5.1 Add Scaladoc for new public APIs, options, key identity, and optional drain capability.
- [x] 5.2 Update README and `docs/porting-notes.md` for typed global listeners, editor insertion, opt-in forced auto-apply, optional drain support, and Insert key parity.
- [x] 5.3 Update `docs/keybinding-defaults.md` if Insert becomes part of documented defaults or examples.
- [x] 5.4 Run focused tests for TUI input routing, editor insertion, autocomplete, terminal input parsing, keybinding matching, and terminal shutdown behavior.
- [x] 5.5 Run `mill __.compile`, `mill core.test`, `mill scalafmtCheck`, `mill scalafixCheck`, and `openspec validate --all --strict`.
