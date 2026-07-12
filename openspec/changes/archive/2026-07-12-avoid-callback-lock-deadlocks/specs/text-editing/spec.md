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

### Requirement: Filter paste appends without per-chunk filtering
`SelectList` and `SettingsList` SHALL use one shared package-private paste session built from `StringBuilder` and `TerminalUtf8Decoder`. The session SHALL retain the initial committed query `String` by reference, store only newly decoded normalized pasted text in its appended builder, and cache at most one immutable full-query snapshot.

#### Scenario: Filter chunks stream
- **WHILE** a filter paste streams
- **WHEN** chunks arrive
- **THEN** the component decodes, normalizes CR and LF to spaces, and appends each decoded segment once without filtering, clamping, selecting, callbacks, or rendering

#### Scenario: Filter query snapshot is reused
- **WHILE** no non-empty normalized decoded text has been accepted since the last query snapshot
- **WHEN** query, render, or commit reads the query
- **THEN** the session returns the same cached `String` reference without rebuilding it

#### Scenario: Accepted filter text invalidates the snapshot
- **WHEN** non-empty normalized decoded text is appended or emitted by decoder flush
- **THEN** the session immediately releases any stale combined snapshot, points cached storage to `initialQuery`, marks the query dirty without rebuilding it, and the next read builds one exact combined snapshot without an intermediate appended snapshot

#### Scenario: Additional accepted filter text remains lazy
- **WHILE** the query is dirty
- **WHEN** additional non-empty normalized decoded segments are accepted
- **THEN** the session retains only `initialQuery` plus appended mutable text and does not materialize a combined snapshot until the next query read

#### Scenario: Filter paste completes or is interrupted
- **WHEN** paste ends or a non-paste input arrives
- **THEN** the component flushes and commits accepted text once, recomputes final filter selection state once, and handles any later input after that commit

#### Scenario: Active filter paste is observed
- **WHILE** a filter paste is active
- **WHEN** query or render is called explicitly
- **THEN** query and the Settings search prompt expose accepted text while rendered candidates remain filtered against the committed query without changing selection or clamp state

#### Scenario: Filter paste framing is irregular
- **WHEN** paste is empty, a chunk or end is orphaned, or start repeats
- **THEN** empty and orphan events are no-ops and repeated start commits prior accepted content before opening a new session
