## MODIFIED Requirements

### Requirement: Paste cursor accounting spans chunk boundaries
Input paste handling SHALL carry bounded Unicode 17.0.0 UAX #29 default extended grapheme cluster state across `PasteChunk` and fragmented UTF-8 boundaries and place the cursor after the complete grapheme sequence.

#### Scenario: Grapheme spans paste chunks
- **WHEN** the code points of one Unicode 17.0.0 default extended grapheme cluster arrive in different paste chunks
- **THEN** cursor accounting counts that cluster once and places the cursor after it

#### Scenario: UTF-8 spans paste chunks
- **WHEN** the UTF-8 bytes of code points in one Unicode 17.0.0 default extended grapheme cluster arrive in different transport chunks
- **THEN** decoding and cursor accounting preserve the complete cluster and place the cursor after it

#### Scenario: Required rule interactions span chunks
- **WHEN** Hangul, Indic conjunct, combining, GB11 extended pictographic, or regional-indicator sequences cross paste chunk boundaries
- **THEN** Input cursor accounting produces the same cluster count and final cursor as whole-string segmentation

#### Scenario: Chunking does not change accounting
- **WHEN** the same paste is delivered whole, at every single code-point split, one code point per chunk, or with fragmented UTF-8
- **THEN** Input and Editor place the cursor at the same final Unicode grapheme position

#### Scenario: Paste accounting state remains bounded
- **WHEN** streamed paste content grows without a core content-size limit
- **THEN** grapheme accounting state remains bounded independently of the application-owned pasted content
