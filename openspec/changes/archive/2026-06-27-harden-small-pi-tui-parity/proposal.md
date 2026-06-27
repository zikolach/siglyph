## Why

Recent upstream `pi-tui` releases and the latest local parity review show a small set of focused parity gaps that are safer to close before larger layout work. This change keeps parity hardening bounded to keybinding defaults, submit undo behavior, and test-backed audits for Markdown and word-navigation edge cases.

## What Changes

- Add an upstream-aligned `Ctrl+J` newline command path where the typed terminal input model can represent it safely.
- Keep bare line-feed ambiguity explicit instead of treating every `\n` byte as a Ctrl+J shortcut.
- Clear the editor undo stack after submit so post-submit undo cannot resurrect submitted draft state.
- Add focused parity coverage for Markdown streaming code-fence stability.
- Add focused parity coverage for fullwidth punctuation word-boundary behavior and either align behavior or document the deviation.
- Do not add runtime dependencies.
- Do not change the larger editor wrapping or sticky visual-column model in this change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `keybinding-management`: Default keybindings must include the upstream `Ctrl+J` newline alias where typed input can represent it safely.
- `text-editing`: Editor submit must clear undo state, and word-navigation parity coverage must include fullwidth punctuation boundaries.
- `markdown-rendering`: Markdown rendering must keep fenced-code output stable when content streams partial closing fences.

## Impact

- Affected code: keybinding defaults, terminal input parsing tests if needed, editor submit/undo behavior, word-navigation tests, Markdown renderer tests.
- Affected docs: keybinding defaults and porting notes if `Ctrl+J` or bare line-feed behavior needs clarification.
- Affected APIs: no new public API is expected.
- Dependencies: no runtime or test dependency additions are expected.
