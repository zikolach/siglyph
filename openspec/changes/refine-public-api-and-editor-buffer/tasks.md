## 1. API Refinement

- [ ] 1.1 Review current `Component`, `Focusable`, `TUI`, and input routing APIs for minimal result/command changes
- [ ] 1.2 Define a small input handling result model for handled/ignored, render request behavior, and exit request
- [ ] 1.3 Update `TUI` input dispatch to respect the result model while preserving existing simple component behavior where practical
- [ ] 1.4 Add regression tests for handled input, ignored input without redundant render, and component-requested exit

## 2. Editor Buffer Model

- [ ] 2.1 Add pure `EditorBuffer` data model with logical lines and cursor coordinates
- [ ] 2.2 Add constructors/from-text helpers and text export preserving logical newlines
- [ ] 2.3 Implement logical cursor clamping and left/right/up/down movement by grapheme cluster coordinates
- [ ] 2.4 Implement printable Unicode insertion and cursor advancement
- [ ] 2.5 Implement Backspace/Delete grapheme deletion and line merge behavior
- [ ] 2.6 Implement newline insertion and line split behavior
- [ ] 2.7 Implement delete-to-end-of-line and word-delete helper operations needed by future editor input handling
- [ ] 2.8 Implement multiline paste insertion preserving Unicode and logical line breaks

## 3. Tests

- [ ] 3.1 Add editor buffer tests for initialization, cursor clamping, and text export
- [ ] 3.2 Add editor buffer tests for ASCII, CJK, combining mark, emoji, and multi-codepoint grapheme insertion/deletion
- [ ] 3.3 Add editor buffer tests for line split, Backspace merge, and Delete merge
- [ ] 3.4 Add editor buffer tests for delete-to-end, word deletion, and multiline paste
- [ ] 3.5 Verify editor buffer compiles through both JVM core and Scala Native core mirror modules

## 4. Documentation and Follow-up Planning

- [ ] 4.1 Update README or docs with the editor-buffer foundation and non-goals for this change
- [ ] 4.2 Update porting notes with any intentional `pi-tui` deviations introduced by the API/result model
- [ ] 4.3 Add a follow-up roadmap note for rendered editor, overlays/autocomplete, terminal polish, Markdown, and final API stabilization

## 5. Validation

- [ ] 5.1 Run `mill __.compile`
- [ ] 5.2 Run `mill core.test`
- [ ] 5.3 Run `mill interactiveNativeDemo.nativeLink` to ensure Native mirror remains buildable
- [ ] 5.4 Run `openspec validate --all --strict`
