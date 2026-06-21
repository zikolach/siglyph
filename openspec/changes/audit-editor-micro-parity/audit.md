# Editor Micro-Parity Audit

## Status values

Each reviewed behavior uses one status from this full set:

- already covered
- missing test only
- behavior gap
- intentional deviation

## Upstream sources used

- `/Users/nikolay.kushin/.cache/checkouts/github.com/earendil-works/pi/packages/tui/src/components/editor.ts`
- `/Users/nikolay.kushin/.cache/checkouts/github.com/earendil-works/pi/packages/tui/src/editor-component.ts`
- `/Users/nikolay.kushin/.cache/checkouts/github.com/earendil-works/pi/packages/tui/src/autocomplete.ts`
- `/Users/nikolay.kushin/.cache/checkouts/github.com/earendil-works/pi/packages/tui/src/components/input.ts`
- `/Users/nikolay.kushin/.cache/checkouts/github.com/earendil-works/pi/packages/tui/test/editor.test.ts`
- `/Users/nikolay.kushin/.cache/checkouts/github.com/earendil-works/pi/packages/tui/test/autocomplete.test.ts`
- `/Users/nikolay.kushin/.cache/checkouts/github.com/earendil-works/pi/packages/tui/test/input.test.ts`
- `/Users/nikolay.kushin/.cache/checkouts/github.com/earendil-works/pi/packages/tui/test/word-navigation.test.ts`

## Local sources used

- `core/src/scalatui/components/Editor.scala`
- `core/src/scalatui/components/EditorLayout.scala`
- `core/src/scalatui/components/Input.scala`
- `core/src/scalatui/editing/EditorBuffer.scala`
- `core/src/scalatui/editing/WordNavigation.scala`
- `core/src/scalatui/autocomplete/Autocomplete.scala`
- `core/src/scalatui/terminal/KeybindingManager.scala`
- `core/src/scalatui/terminal/TerminalInputParser.scala`
- `core/test/src/scalatui/components/EditorSuite.scala`
- `core/test/src/scalatui/components/EditorLayoutSuite.scala`
- `core/test/src/scalatui/components/ComponentsSuite.scala`
- `core/test/src/scalatui/editing/EditorBufferSuite.scala`
- `core/test/src/scalatui/editing/WordNavigationSuite.scala`
- `core/test/src/scalatui/autocomplete/AutocompleteSuite.scala`
- `core/test/src/scalatui/terminal/KeybindingManagerSuite.scala`
- `core/test/src/scalatui/terminal/TerminalInputParserSuite.scala`

## Audit matrix

| Area | Upstream behavior | Upstream reference | Local reference | Status | Notes |
| --- | --- | --- | --- | --- | --- |
| Prompt history | Empty history leaves text unchanged. | `editor.test.ts:43` | `EditorSuite.scala` command history tests | already covered | Local `Editor.handleInputResult(Up)` returns `NoRender` when no history exists. |
| Prompt history | Up browses most recent, repeated Up reaches older entries, and Down restores newer draft state. | `editor.test.ts:51`, `editor.test.ts:62`, `editor.test.ts:102` | `EditorSuite.scala` command history tests | already covered | Local history is stored newest first and Down exits to empty draft. |
| Prompt history | Typing exits history mode. | `editor.test.ts:127` | `EditorSuite.scala` command history tests | already covered | Local typed character mutates the browsed text and later Down does not restore a stale entry. |
| Prompt history | `setText` exits history mode. | `editor.test.ts:138` | `EditorSuite.scala` `setText exits history browsing` | missing test only | Behavior already existed through `Editor.setText`; focused test added. |
| Prompt history | Empty history entries are ignored. | `editor.test.ts:152` | `EditorSuite.scala` `history ignores empty and consecutive duplicate entries` | missing test only | Behavior already existed in `Editor.addToHistory`; focused test added. |
| Prompt history | Consecutive duplicate history entries are ignored. | `editor.test.ts:167` | `EditorSuite.scala` `history ignores empty and consecutive duplicate entries` | missing test only | Behavior already existed in `Editor.addToHistory`; focused test added. |
| Prompt history | Non-consecutive duplicate history entries are preserved. | `editor.test.ts:181` | `EditorSuite.scala` `history keeps non-consecutive duplicates and caps at one hundred entries` | missing test only | Behavior already existed in `Editor.addToHistory`; focused test added. |
| Prompt history | History is capped at 100 entries. | `editor.test.ts:214` | `EditorSuite.scala` `history keeps non-consecutive duplicates and caps at one hundred entries` | missing test only | Behavior already existed in `Editor.addToHistory`; focused test added. |
| Prompt history | Up from non-empty draft first moves the cursor to the start before browsing history. | `editor.test.ts:82` | `Editor.scala` `shouldUseHistoryUp`; `EditorSuite.scala` command history tests | behavior gap | Local Up on single-line non-empty draft is plain cursor movement to the previous logical line, so it does not jump to line start. |
| Prompt history | Browsed multi-line history supports opposite-direction cursor movement. | `editor.test.ts:269` | `Editor.scala` history browsing and cursor movement | behavior gap | Local history browsing does not keep a separate history-navigation mode once cursor movement mutates position. |
| Public editor state | Cursor position and lines can be read without exposing mutable internals. | `editor.test.ts:287`, `editor.test.ts:303` | `Editor.cursor`, `Editor.lines`, `EditorBufferSuite.scala` | already covered | Scala returns immutable `EditorCursor` and `Vector[String]` values. |
| Backslash newline workaround | A standalone backslash before Enter inserts a newline. | `editor.test.ts:315` | `EditorEnterBehavior`, `TerminalInputParserSuite.scala`; `docs/keybinding-defaults.md` | intentional deviation | Scala uses typed `Enter` with configurable newline modifiers and does not emulate the raw backslash workaround. |
| Kitty and modified printable input | Unsupported modified printable CSI-u is ignored and shifted printable input is inserted. | `editor.test.ts:373` | `TerminalInputParserSuite.scala`; `EditorSuite.scala` printable input tests | already covered | Parser normalizes supported modified printable events; editor ignores shortcut-modified printable input. |
| Unicode editing | ASCII, umlauts, emoji, and mixed Unicode insert and delete by grapheme. | `editor.test.ts:399` | `EditorBufferSuite.scala` insertion and deletion tests | already covered | Local buffer uses Unicode grapheme clusters. |
| Unicode editing | Cursor moves across emoji and inserts after cursor movement. | `editor.test.ts:446`, `editor.test.ts:464` | `EditorBufferSuite.scala`; `EditorSuite.scala` cursor movement tests | already covered | Local cursor columns are grapheme-cluster indices. |
| Unicode editing | Unicode text is preserved across line breaks and full replacement. | `editor.test.ts:484`, `editor.test.ts:499` | `EditorBufferSuite.scala`; `Editor.setText` | already covered | Local newline normalization and `EditorBuffer.apply` preserve Unicode text. |
| Word movement | Ctrl+W, Alt+Backspace, Ctrl+Left, and Ctrl+Right follow punctuation and Unicode word boundaries. | `editor.test.ts:521`, `editor.test.ts:571`; `word-navigation.test.ts:5` | `WordNavigationSuite.scala`; `EditorSuite.scala` word tests | already covered | Local `WordNavigation` covers punctuation, CJK, and atomic segments. |
| Word movement | Word movement stops at fullwidth Chinese punctuation. | `editor.test.ts:625` | `WordNavigationSuite.scala`; `UnicodeSuite.scala` | behavior gap | Local tests cover CJK mixed words but do not prove fullwidth punctuation boundaries, and current `WordNavigation` needs a focused follow-up before claiming parity. |
| Visual wrapping | Wide emoji, CJK, combining marks, and cursor rendering stay width-safe. | `editor.test.ts:702`, `editor.test.ts:735`, `editor.test.ts:747`, `editor.test.ts:783`, `editor.test.ts:799` | `EditorLayoutSuite.scala`; `EditorSuite.scala` render tests | already covered | Local layout uses display width and grapheme clusters. |
| Visual wrapping | Word wrapping prefers word boundaries and handles whitespace boundary cases. | `editor.test.ts:835` | `EditorLayout.scala` | behavior gap | Local editor wraps by grapheme/display width and does not implement word-boundary wrapping. |
| Visual wrapping | Oversized atomic paste marker segments split safely and do not crash at narrow widths. | `editor.test.ts:1035`, `editor.test.ts:3547` | `EditorBuffer.scala`; `EditorSuite.scala` large paste tests | already covered | Local runtime sanitization and marker-aware clusters keep output width-safe. |
| Kill ring | Ctrl+W, Ctrl+U, Ctrl+K, Ctrl+Y, Alt+Y, accumulation, rotation, middle yank, and multiline yank behavior match editor semantics. | `editor.test.ts:1158`; `input.test.ts:86` | `EditorSuite.scala`; `ComponentsSuite.scala` input kill-ring tests | already covered | Local editor and input share `KillRing` behavior. |
| Kill ring | Large paste marker is deleted as one logical unit. | `editor.test.ts:3609`, `editor.test.ts:3631` | `EditorSuite.scala` `large paste marker is edited as one logical unit` | missing test only | Behavior already existed through marker-aware clusters; focused test added. |
| Undo | Empty undo, coalesced word typing, space units, backspace, delete, Ctrl+W, Ctrl+K, Ctrl+U, yank, paste, and cursor movement boundaries are undoable. | `editor.test.ts:1555`; `input.test.ts:422` | `EditorSuite.scala`; `ComponentsSuite.scala`; `EditorBufferSuite.scala` | already covered | Local undo-only stack behavior is covered at editor and input level. |
| Undo | Submit clears the undo stack. | `editor.test.ts:1907` | `Editor.scala` `submit` | behavior gap | Local submit invokes the callback but does not clear `UndoStack`. |
| Undo | `insertTextAtCursor` is public and undoable. | `editor.test.ts:1827`, `editor.test.ts:1846`, `editor.test.ts:1867` | `EditorBuffer.insert`; `Editor.handleInputResult` | intentional deviation | Scala does not expose a public `insertTextAtCursor` editor API; applications use typed input, paste input, or `setText`. |
| Autocomplete | Slash autocomplete renders suggestions, navigates, confirms, and respects custom select bindings. | `editor.test.ts:2092`, `editor.test.ts:2484`, `editor.test.ts:2540` | `EditorSuite.scala` autocomplete tests | already covered | Local overlay-backed autocomplete supports navigation, Tab/Enter confirmation, and custom bindings. |
| Autocomplete | Debounce, abort, stale response, stale failure, pending visible suggestions, empty result close, and provider replacement cancellation are handled. | `editor.test.ts:2230`, `editor.test.ts:2407`; `autocomplete.test.ts:57` | `EditorSuite.scala`; `AutocompleteSuite.scala` | already covered | Local callback provider model cancels stale work and ignores late results. |
| Autocomplete | Force-file autocomplete auto-applies one suggestion. | `editor.test.ts:2093` | `AutocompleteSuite.scala`; `EditorSuite.scala` | intentional deviation | Scala autocomplete providers insert replacements through explicit selection and keep command semantics application-owned. |
| Autocomplete | Slash command argument completers are awaited and invalid results are ignored. | `editor.test.ts:2749`, `editor.test.ts:2774`, `editor.test.ts:2797` | `Autocomplete.scala`; `AutocompleteSuite.scala` | intentional deviation | Scala exposes generic provider composition and does not model pi-tui slash command argument completer internals. |
| Character jump | Ctrl+] and Ctrl+Alt+] jump forward/backward, cancel modes, search special characters, and reset action state. | `editor.test.ts:2824` | `EditorSuite.scala` jump tests | already covered | Local jump tests cover forward/backward movement and typed target routing. |
| Sticky column | Up/down preserve preferred visual column through short lines, wrapped lines, typing, movement resets, undo, and resize. | `editor.test.ts:3045` | `Editor.scala`; `EditorLayout.scala` | behavior gap | Local movement uses logical line movement and does not store a preferred visual column. |
| Paste marker expansion | Submitted text expands large paste marker content literally. | `editor.test.ts:3824`, `editor.test.ts:4025` | `EditorSuite.scala` large paste submit test; `EditorBufferSuite.scala` marker expansion tests | already covered | Local `submitText` expands markers before callback delivery. |
| Input component | Input submit, backslash insertion, wide render safety, kill ring, yank-pop, undo, and word deletion match focused input semantics. | `input.test.ts:6` | `ComponentsSuite.scala`; `Input.scala` | already covered | Local input is single-line and typed-input based. |

## Follow-up OpenSpec proposal candidates

### Candidate: Add editor word-boundary wrapping

- Related matrix rows: visual wrapping word-boundary behavior.
- Scope: teach `EditorLayout` to prefer word boundaries while preserving grapheme and wide-cell safety.
- Validation target: upstream `editor.test.ts:835` through `editor.test.ts:1017`.

### Candidate: Add editor sticky visual column movement

- Related matrix rows: sticky column behavior and multi-line history cursor movement.
- Scope: store preferred visual column for vertical movement across logical and wrapped lines, reset it on horizontal edits and mutations, and handle resize rewraps.
- Validation target: upstream `editor.test.ts:3045` through `editor.test.ts:3520`.

### Candidate: Align history browsing cursor edge cases

- Related matrix rows: non-empty draft Up behavior and multi-line history opposite-direction movement.
- Scope: add a history-browsing mode that separates draft cursor positioning from history replacement.
- Validation target: upstream `editor.test.ts:82`, `editor.test.ts:235`, `editor.test.ts:250`, and `editor.test.ts:269`.

### Candidate: Clear editor undo stack on submit

- Related matrix rows: submit clears undo stack.
- Scope: clear editor undo state after successful submit while preserving current submit callback semantics.
- Validation target: upstream `editor.test.ts:1907`.

### Candidate: Audit fullwidth punctuation word boundaries

- Related matrix rows: fullwidth Chinese punctuation word movement.
- Scope: add focused `WordNavigation` fixtures for fullwidth punctuation and decide whether current punctuation grouping should change.
- Validation target: upstream `editor.test.ts:625`.
