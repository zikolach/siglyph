## ADDED Requirements

### Requirement: Editor streamed paste transaction
The multiline Editor SHALL treat one `PasteStart` through `PasteEnd` stream as one logical edit while keeping parser and runtime transit bounded.

#### Scenario: Whole paste semantics span chunks
- **WHEN** bracketed paste bytes cross parser, UTF-8 decoder, CRLF, and Unicode grapheme boundaries
- **THEN** newline normalization, aggregate line and grapheme thresholds, marker content, and cursor placement use the complete normalized paste

#### Scenario: Paste completion has one edit effect
- **WHEN** a non-empty paste completes
- **THEN** the Editor captures one undo snapshot, invokes `onChange` once, refreshes active autocomplete once, and requests one render

#### Scenario: Empty paste has no effect
- **WHEN** `PasteStart` is followed by `PasteEnd` without accepted content
- **THEN** text, cursor, undo, callbacks, autocomplete, and rendering remain unchanged

#### Scenario: Non-paste input commits an unfinished paste
- **WHEN** any non-paste input arrives during an unfinished paste session
- **THEN** the Editor commits all accepted normalized paste content as one edit before handling that input

#### Scenario: Large paste remains expandable
- **WHEN** aggregate normalized content exceeds 10 lines or 1000 grapheme clusters
- **THEN** one marker stores the complete normalized content and submit or expansion recovers it exactly

### Requirement: Paste cursor accounting spans chunk boundaries
Input paste handling SHALL carry Unicode grapheme-break state across `PasteChunk` boundaries and place the cursor after the complete grapheme sequence.

#### Scenario: Grapheme spans paste chunks
- **WHEN** the code points of one grapheme cluster arrive in different paste chunks
- **THEN** cursor accounting counts that cluster once and places the cursor after it

### Requirement: Input paste appends incrementally
Input SHALL use one mutable paste session containing a prefix builder, appended decoded and newline-normalized paste text, and one retained suffix. It SHALL finalize that session once at `PasteEnd` or before handling a non-paste interruption.

#### Scenario: Accepted chunks append once
- **WHILE** paste chunks arrive
- **WHEN** each decoded non-empty segment is accepted
- **THEN** Input appends that segment once without rebuilding prior pasted text or rescanning the accumulated prefix

#### Scenario: Active paste is publicly observable without intermediate rendering
- **WHILE** a paste session is active
- **WHEN** `value` or `render` is called after accepted chunks
- **THEN** it exposes the accepted prefix, pasted text, retained suffix, and incremental grapheme cursor while paste start and chunks request no render

#### Scenario: Paste finalizes once
- **WHEN** `PasteEnd` or a non-paste input ends an active paste
- **THEN** Input publishes one immutable value, preserves exact text and one undo boundary, and requests no chunk-driven intermediate render

#### Scenario: Grapheme state spans every insertion position
- **WHEN** combining marks, ZWJ sequences, or regional-indicator pairs cross chunks at the start, middle, or end cursor position
- **THEN** incremental grapheme state places backspace immediately after the complete pasted grapheme
