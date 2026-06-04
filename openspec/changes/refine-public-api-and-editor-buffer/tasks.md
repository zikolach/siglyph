## 1. API Refinement

- [x] 1.1 Review current `Component`, `Focusable`, `TUI`, and input routing APIs for minimal result/command changes
- [x] 1.2 Define a small input handling result model for handled/ignored, render request behavior, and exit request
- [x] 1.3 Update `TUI` input dispatch to respect the result model while preserving existing simple component behavior where practical
- [x] 1.4 Add regression tests for handled input, ignored input without redundant render, and component-requested exit

## 2. Editor Buffer Model

- [x] 2.1 Add pure `EditorBuffer` data model with logical lines and cursor coordinates
- [x] 2.2 Add constructors/from-text helpers and text export preserving logical newlines
- [x] 2.3 Implement logical cursor clamping and left/right/up/down movement by grapheme cluster coordinates
- [x] 2.4 Implement printable Unicode insertion and cursor advancement
- [x] 2.5 Implement Backspace/Delete grapheme deletion and line merge behavior
- [x] 2.6 Implement newline insertion and line split behavior
- [x] 2.7 Implement delete-to-end-of-line and word-delete helper operations needed by future editor input handling
- [x] 2.8 Implement multiline paste insertion preserving Unicode and logical line breaks

## 3. Tests

- [x] 3.1 Add editor buffer tests for initialization, cursor clamping, and text export
- [x] 3.2 Add editor buffer tests for ASCII, CJK, combining mark, emoji, and multi-codepoint grapheme insertion/deletion
- [x] 3.3 Add editor buffer tests for line split, Backspace merge, and Delete merge
- [x] 3.4 Add editor buffer tests for delete-to-end, word deletion, and multiline paste
- [x] 3.5 Verify editor buffer compiles through both JVM core and Scala Native core mirror modules

## 4. Documentation and Follow-up Planning

- [x] 4.1 Update README or docs with the editor-buffer foundation and non-goals for this change
- [x] 4.2 Update porting notes with any intentional `pi-tui` deviations introduced by the API/result model
- [x] 4.3 Add a follow-up roadmap note for rendered editor, overlays/autocomplete, terminal polish, Markdown, and final API stabilization
- [x] 4.4 Add Scaladoc for new public API types and methods
- [x] 4.5 Add Scalafmt configuration and documented Mill formatting checks
- [x] 4.6 Add Scalafix configuration and documented Mill baseline best-practice checks

## 5. Validation

- [x] 5.1 Run `mill __.compile`
- [x] 5.2 Run `mill core.test`
- [x] 5.3 Run `mill interactiveNativeDemo.nativeLink` to ensure Native mirror remains buildable
- [x] 5.4 Run `mill scalafmtCheck`
- [x] 5.5 Run `mill scalafixCheck`
- [x] 5.6 Run `openspec validate --all --strict`
