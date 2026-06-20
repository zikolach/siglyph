## 1. Audit setup

- [ ] 1.1 Identify upstream `pi-tui` editor-related source and test files used for the audit.
- [ ] 1.2 Identify local Scala editor, input, autocomplete, keybinding, and rendering tests used for comparison.
- [ ] 1.3 Create an audit matrix with the exact status values: already covered, missing test only, behavior gap, intentional deviation.

## 2. Behavior comparison

- [ ] 2.1 Audit editor buffer mutation, cursor movement, undo, kill-ring, paste, and submit extraction behavior.
- [ ] 2.2 Audit editor visual wrapping, Unicode cursor placement, fake cursor, hardware cursor marker, and narrow-width layout behavior.
- [ ] 2.3 Audit autocomplete refresh, stale response handling, navigation, completion, cancellation, and overlay interaction behavior.
- [ ] 2.4 Audit input and editor keybindings for movement, deletion, history, submit/newline, jump, page navigation, undo, yank, and yank-pop.

## 3. Test and follow-up work

- [ ] 3.1 Add missing local tests only for behaviors that already match upstream.
- [ ] 3.2 Record behavior gaps as follow-up OpenSpec proposal candidates without changing product behavior in this audit.
- [ ] 3.3 Update porting notes for intentional deviations found during the audit.

## 4. Validation

- [ ] 4.1 Run relevant core editor/input/autocomplete tests.
- [ ] 4.2 Run `mill core.test`, `mill __.compile`, `mill scalafmtCheck`, `mill scalafixCheck`, and `openspec validate --all --strict`.
