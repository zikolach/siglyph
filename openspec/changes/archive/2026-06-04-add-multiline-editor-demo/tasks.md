## 1. Editor API and Options

- [x] 1.1 Define public `EditorEnterBehavior` and editor options with Scaladoc
- [x] 1.2 Define public `Editor` component API with initial text, callbacks, focus support, and Scaladoc
- [x] 1.3 Ensure editor API compiles in both JVM core and Scala Native core mirror modules without new runtime dependencies

## 2. Editor Layout

- [x] 2.1 Add pure editor visual layout model for wrapped lines and cursor visual position
- [x] 2.2 Implement width-aware wrapping from logical `EditorBuffer` lines to visual lines
- [x] 2.3 Implement logical cursor to visual row/column mapping for wrapped lines
- [x] 2.4 Add layout tests for long lines, empty lines, CJK, combining marks, emoji, and cursor-at-end cases

## 3. Editor Rendering

- [x] 3.1 Implement `Editor.render(width)` using editor layout and `EditorBuffer`
- [x] 3.2 Render inverse-video fake cursor when focused and no fake cursor when unfocused
- [x] 3.3 Ensure every rendered line satisfies the component visible-width contract
- [x] 3.4 Add render tests for focused cursor, unfocused rendering, wrapping, empty lines, and width limits

## 4. Editor Input Handling

- [x] 4.1 Implement printable character and paste insertion via `EditorBuffer`
- [x] 4.2 Implement Backspace, Delete, Ctrl+K, Ctrl+W, and Alt/Ctrl+Backspace deletion behavior
- [x] 4.3 Implement Left, Right, Up, Down, Home, End, Ctrl+A, and Ctrl+E cursor movement behavior
- [x] 4.4 Implement configurable Enter behavior for submit-on-Enter plus Shift+Enter newline
- [x] 4.5 Implement configurable Enter behavior for newline-on-Enter plus Cmd/Super+Enter submit
- [x] 4.6 Invoke change callback for buffer mutations and submit callback for configured submit events
- [x] 4.7 Return `InputResult.Render`, `InputResult.NoRender`, or equivalent results accurately for handled and ignored input
- [x] 4.8 Add input tests covering key handling, callbacks, Enter behavior modes, and Unicode/paste input

## 5. Interactive Editor Demo

- [x] 5.1 Add shared multiline editor demo construction and state management
- [x] 5.2 Add JVM editor demo target or integrate an editor mode into the existing JVM interactive demo
- [x] 5.3 Add Scala Native editor demo target or integrate an editor mode into the existing Native interactive demo
- [x] 5.4 Ensure the editor demo exits safely on Escape and Ctrl+C
- [x] 5.5 Document editor demo launch commands and controls in README or docs

## 6. Documentation and Porting Notes

- [x] 6.1 Document public editor API behavior, callbacks, Enter behavior, and non-goals
- [x] 6.2 Update porting notes with `pi-tui` editor parity scope and intentional deferrals
- [x] 6.3 Document follow-up work for autocomplete/overlays, undo/kill-ring, large paste markers, IME cursor, and hardware cursor positioning

## 7. Validation

- [x] 7.1 Run `mill scalafmtCheck`
- [x] 7.2 Run `mill scalafixCheck`
- [x] 7.3 Run `mill quality`
- [x] 7.4 Run `mill __.compile`
- [x] 7.5 Run `mill core.test`
- [x] 7.6 Run JVM editor demo compile or TTY smoke test where feasible
- [x] 7.7 Run Native editor demo build (`nativeLink`) or TTY smoke test where feasible
- [x] 7.8 Run `openspec validate --all --strict`
