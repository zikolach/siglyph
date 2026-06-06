## 1. Upstream Verification

- [ ] 1.1 Re-read current upstream `pi-tui` `keybindings.ts`, `components/editor.ts`, and related key parser helpers before coding
- [ ] 1.2 Record any intentional Scala deviations for unsupported terminal encodings in the design or docs
- [ ] 1.3 Add a test fixture or documented mapping table for upstream default command ids and default key descriptors

## 2. Keybinding Model and Manager

- [ ] 2.1 Add shared core keybinding command ids, key descriptors, definitions, default bindings, and conflict data types
- [ ] 2.2 Implement a keybinding manager that resolves defaults, replaces defaults with user bindings, supports explicit empty bindings, matches typed `TerminalInput`, and exposes conflicts
- [ ] 2.3 Add tests for default matches, overrides, omitted defaults, disabled commands, duplicate keys, and unknown command handling
- [ ] 2.4 Add Scaladoc for public keybinding types and override semantics

## 3. Terminal Input Coverage

- [ ] 3.1 Add or verify typed parser support for PageUp/PageDown key events and modifiers needed by default bindings
- [ ] 3.2 Add or verify typed parser support for `Ctrl+]`, `Ctrl+Alt+]`, `Ctrl+-`, and other remaining default bindings where distinguishable
- [ ] 3.3 Add parser tests for representative common escape/control sequences on JVM/Native shared input paths
- [ ] 3.4 Document ambiguous or unsupported terminal-specific bindings with closest supported behavior

## 4. Editor/Input Dispatch Integration

- [ ] 4.1 Refactor `Input` and `Editor` command dispatch to use the configured keybinding manager instead of scattered hard-coded key checks
- [ ] 4.2 Preserve current default behavior for implemented movement, deletion, undo, kill-ring, yank/yank-pop, paste, submit, newline, and Tab/autocomplete commands
- [ ] 4.3 Add configuration plumbing so applications can supply custom keybindings without breaking existing constructors
- [ ] 4.4 Add tests proving custom submit/newline/movement/delete bindings affect `Input` and `Editor` behavior

## 5. Remaining Editor Parity Features

- [ ] 5.1 Implement editor prompt history storage/navigation with trim, empty-ignore, consecutive-dedupe, cap-100, undo snapshot, change callbacks, and Up/Down boundary behavior
- [ ] 5.2 Implement PageUp/PageDown cursor movement by visible page using wrapped visual layout and desired-column clamping
- [ ] 5.3 Implement forward/backward jump-to-character mode with cancel-on-repeat, printable-character search, multi-line traversal, and no-match no-op behavior
- [ ] 5.4 Add tests for history navigation, page movement, jump mode, and interactions with undo/change callbacks

## 6. Autocomplete and Selection Precedence

- [ ] 6.1 Route visible autocomplete overlay input through configurable select/input keybindings before normal editor movement/history commands
- [ ] 6.2 Preserve Tab accept/refresh, Escape/Ctrl+C cancel, Up/Down navigation, and Enter accept behavior including slash-command submit fall-through
- [ ] 6.3 Add tests for custom select bindings and default autocomplete precedence over editor cursor/history commands

## 7. Documentation and Validation

- [ ] 7.1 Update README/docs/smoke notes with default controls, customization examples, history/page/jump behavior, and known parser deviations
- [ ] 7.2 Ensure no new runtime dependencies are added
- [ ] 7.3 Run `mill core.test`, relevant demo/component tests, `mill __.compile`, and `openspec validate --all --strict`
