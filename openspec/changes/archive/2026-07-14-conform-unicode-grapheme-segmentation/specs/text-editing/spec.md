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

## ADDED Requirements

### Requirement: Insertion cursor follows final grapheme segmentation
Input, EditorBuffer, and Editor SHALL place the cursor at the first final grapheme boundary at or after the insertion end after resegmenting the resulting value.

#### Scenario: Inserted text joins neighboring graphemes
- **WHEN** typed, programmatic, or streamed inserted code points join the left neighbor, right neighbor, or both through combining, prepend, Hangul, Indic, GB11, or regional-indicator rules
- **THEN** the cursor is a valid boundary in the final segmentation immediately after the resulting joined cluster
- **AND** immediate backward deletion removes the cluster before that cursor

### Requirement: Input yank-pop restores the pre-yank state
Input SHALL retain the exact pre-yank `Input.State` after a successful yank. Yank-pop SHALL restore
that state before inserting the rotated kill-ring candidate. Another completed editing action SHALL
clear the retained yank base.

#### Scenario: Yanked text joins neighboring graphemes
- **WHEN** yanked text joins a left or right neighbor through combining, prepend, Hangul, Indic, GB11, or regional-indicator rules
- **THEN** yank-pop restores the original value and cursor before inserting the rotated candidate
- **AND** original neighboring content survives replacement

#### Scenario: Repeated yank-pop uses one base state
- **WHEN** yank-pop runs repeatedly after one successful yank
- **THEN** each rotated candidate replaces the prior yank from the same exact pre-yank state

#### Scenario: Another edit ends the yank chain
- **WHEN** another editing action completes after yank
- **THEN** the retained pre-yank state is cleared and yank-pop does not replace text

### Requirement: Editing cursor metadata follows visible fake cursor ownership
Focused Input and Editor SHALL attach structured frame-relative cursor metadata only when they own
input. Normal rows SHALL attach a candidate only when the complete fake cursor token survives width
truncation. A focused Editor positive impossible-width owner row SHALL attach column-zero metadata
without printable fake-cursor content. Editor width zero or below, unfocused rendering, and
autocomplete-owned rendering SHALL attach no cursor metadata.

#### Scenario: Focused normal-width cursor survives
- **WHEN** a focused Input or Editor fake cursor fits within the requested width
- **THEN** rendering attaches its zero-based display-cell coordinate

#### Scenario: Normal cursor token is truncated
- **WHEN** width truncation omits the complete fake cursor token
- **THEN** the normal row contains no cursor metadata

#### Scenario: Editor impossible-width row owns the cursor
- **WHEN** a focused Editor cursor owns a blank row for an over-wide cluster and autocomplete does not own input
- **THEN** rendering attaches cursor metadata at column zero without printable fake-cursor content

#### Scenario: Editing component does not own input
- **WHEN** Input or Editor is unfocused, or Editor autocomplete owns input
- **THEN** rendering attaches no cursor metadata

### Requirement: EditorBuffer source remains exact through display projection
EditorBuffer SHALL retain exact unlimited source text and Unicode 17.0.0 source-grapheme cursor
positions. ANSI sanitization and Editor display projection SHALL NOT mutate, truncate, replace, or
cap retained source.

#### Scenario: Rejected source expands to several display units
- **WHEN** one retained source range sanitizes to several visible graphemes or wrapped rows
- **THEN** EditorBuffer text and cursor positions remain exact
- **AND** display projection owns every output unit with the original half-open source range

## REMOVED Requirements

### Requirement: Hardware cursor marker support
**Reason**: Focused editing components no longer emit a string cursor sentinel. The public `CursorMarker` object and its sentinel APIs were removed with no compatibility path.
**Migration**: Focused Input and Editor rendering SHALL attach structured `CursorPlacement` values according to `Editing cursor metadata follows visible fake cursor ownership`. Ordinary former cursor bytes remain inert text.
