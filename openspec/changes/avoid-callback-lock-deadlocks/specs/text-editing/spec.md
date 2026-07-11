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
