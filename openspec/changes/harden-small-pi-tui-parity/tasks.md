## 1. Ctrl+J Newline Parity

- [ ] 1.1 Add `Ctrl+J` as a default `tui.input.newLine` keybinding through typed key descriptors.
- [ ] 1.2 Add parser or keybinding tests for reliable Ctrl+J encodings, including CSI-u `Ctrl+J` when supported by the parser.
- [ ] 1.3 Preserve and document bare line-feed ambiguity so plain Enter behavior does not silently change on terminals that emit `\n` for Return.

## 2. Editor Submit Undo Boundary

- [ ] 2.1 Clear the editor undo stack after submit through the existing submit path.
- [ ] 2.2 Add a focused editor test proving undo after submit does not restore pre-submit draft snapshots.
- [ ] 2.3 Verify submit still closes autocomplete, invokes the submit callback with expanded paste markers, and requests render as before.

## 3. Word Navigation Parity Fixtures

- [ ] 3.1 Add focused word-navigation tests for mixed CJK text separated by fullwidth punctuation.
- [ ] 3.2 If tests reveal a gap, adjust word-boundary logic only for the verified fullwidth punctuation behavior.
- [ ] 3.3 If current behavior already matches, record the fixture as test-only parity coverage.

## 4. Markdown Streaming Fence Stability

- [ ] 4.1 Add Markdown renderer tests for an opening fenced-code block followed by a partial closing fence during streaming.
- [ ] 4.2 Add Markdown renderer tests for a complete closing fence and following normal markdown block parsing.
- [ ] 4.3 If tests reveal a gap, adjust fenced-code parsing without adding parser or highlighter dependencies.

## 5. Documentation and Validation

- [ ] 5.1 Update `docs/keybinding-defaults.md` and `docs/porting-notes.md` if Ctrl+J or bare line-feed behavior changes or needs clarification.
- [ ] 5.2 Run focused tests for keybindings, terminal input parsing, editor behavior, word navigation, and Markdown rendering.
- [ ] 5.3 Run `mill __.compile`, `mill core.test`, `mill markdown.test`, `mill scalafmtCheck`, `mill scalafixCheck`, and `openspec validate --all --strict`.
