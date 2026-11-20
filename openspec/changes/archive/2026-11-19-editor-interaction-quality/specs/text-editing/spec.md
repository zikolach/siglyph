## ADDED Requirements

### Requirement: Editor visual-row arrow navigation
The shared JVM/Native Editor SHALL move Up/Down through reachable rendered visual rows using the private sanitized render projection and preserve a preferred display column through consecutive vertical commands. It SHALL floor within wide units and clamp wrapped row ends to a boundary owned by the target row. Rows sharing source ownership that cannot independently host a cursor SHALL be skipped without modifying source text.

#### Scenario: Short row retains preferred column
- **WHEN** Down moves from a long row through a short row to another long row
- **THEN** the final cursor returns to the initial preferred display column where reachable

#### Scenario: Resize resets preferred column
- **WHEN** a different render width is committed
- **THEN** the next vertical command captures the current cursor's newly projected column

#### Scenario: Nonvertical action resets preferred column
- **WHEN** an explicit cursor placement, nonvertical movement, text mutation, paste completion, undo, history transition, submit or autocomplete completion occurs
- **THEN** the next vertical command starts a new preferred-column sequence

#### Scenario: ANSI and Unicode retain source ownership
- **WHEN** navigation crosses wrapped wide or combining graphemes, ANSI metadata, rejected controls or impossible-width rows
- **THEN** the cursor uses reachable source boundaries from the render projection without splitting or changing retained content

## MODIFIED Requirements

### Requirement: Editor prompt history navigation parity
The editor SHALL support bounded prompt history navigation with exact draft restoration. Up SHALL enter history from an empty editor or deliberately from display column zero on the first visual row of a nonempty draft. While browsing, Up at the first visual row SHALL load older entries and Down at the last visual row SHALL load newer entries; other arrows SHALL navigate visual rows without abandoning history.

#### Scenario: Submitted prompt can be added to history
- **WHEN** application code records a submitted prompt in editor history
- **THEN** empty or whitespace-only prompts and consecutive duplicate prompts after trimming are ignored without changing browsing or preferred column
- **AND** actual additions cap retained history at 100 entries and end the current browse

#### Scenario: Up enters history from empty editor
- **WHEN** the editor is empty and receives the cursor-up command while history contains prompts
- **THEN** it captures the draft once, loads the newest prompt at logical cursor (0,0), emits a change callback if text changes, and records one pre-history undo snapshot

#### Scenario: Deliberate nonempty draft entry
- **WHEN** Up is received at display column zero of the first visual row with history available
- **THEN** the exact draft text, logical cursor and private paste registry are captured before loading history

#### Scenario: Down returns from history to current prompt state
- **WHEN** Down at the last visual row moves past the newest history entry
- **THEN** the original draft text, cursor and paste registry are restored and browsing ends without adding an undo entry
- **AND** cursor-only restoration requests a render without invoking the text-change callback

#### Scenario: Edits leave history browsing mode
- **WHEN** text is mutated, undo is applied, setText or setCursor is called, completion is applied, or submit occurs while browsing
- **THEN** the retained draft is discarded and the currently displayed text remains the editable state

#### Scenario: Empty history
- **WHEN** history is empty and an arrow cannot move to another visual row
- **THEN** text, cursor, callbacks and undo remain unchanged
