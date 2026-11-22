# editor-draft-handoff Specification

## Purpose
Expose committed logical editor text and reusable opaque draft handoff without synthetic submission or loss of compact-paste metadata, while defining restoration side effects and preserving buffer representation compatibility.

## Requirements

### Requirement: Read-only submission projection
Editor SHALL expose `submittedText: String` under its component state boundary, delegating to the buffer submission projection without mutating editor state or invoking callbacks.

#### Scenario: Validate a compact paste
- **WHEN** an application reads submittedText after a completed large typed paste
- **THEN** it receives the logical payload rather than displayed marker length, with all editor state unchanged

#### Scenario: Half-open paste
- **WHEN** submittedText or snapshotDraft is read between PasteStart and PasteEnd
- **THEN** only committed buffer content is observed and the paste session is not changed

### Requirement: Opaque reusable drafts
Editor SHALL expose `snapshotDraft(): EditorDraftSnapshot` and `restoreDraft(snapshot: EditorDraftSnapshot): Unit`. The snapshot SHALL preserve displayed lines, private paste metadata, identity counter and exact logical cursor independently of later edits, without exposing editable registry data.

#### Scenario: Reuse a queued draft
- **WHEN** a snapshot is restored repeatedly after other edits or into another editor
- **THEN** displayed and submitted text and logical cursor are restored exactly, and subsequent pastes do not reuse its registered identities

### Requirement: Marker collision safety preserves compatibility
The buffer SHALL expand registered markers in a single nonrecursive pass without interpreting replacement metacharacters. New marker IDs SHALL avoid marker-shaped IDs already in displayed text, stored payloads or the incoming payload. Numeric parsing SHALL tolerate out-of-range literal IDs.

#### Scenario: Literal marker text inside a payload
- **WHEN** multiple paste payloads contain marker-shaped strings, dollar signs or backslashes
- **THEN** submission preserves each payload literally without recursively expanding payload text or interpreting replacement syntax

#### Scenario: New paste after draft restore
- **WHEN** a saved draft with registered pastes and marker-shaped literal text is restored and another paste is committed
- **THEN** its allocated marker identity does not collide with existing registered or literal IDs

### Requirement: Buffer representation compatibility
The existing public buffer Snapshot shape SHALL remain unchanged. Literal text inserted later that matches an already registered marker SHALL retain legacy alias semantics; this capability does not introduce structural span provenance.

#### Scenario: Existing marker reference syntax
- **WHEN** literal text matching an already registered marker is inserted later
- **THEN** it retains the existing alias interpretation rather than acquiring a new literal identity

### Requirement: Restore invalidates transient editing state
Restore SHALL clear undo, pending paste, history navigation, yank continuation, jump and preferred-column state; cancel autocomplete without refreshing; retain history entries, callbacks, focus, configuration and TUI context; and request render. It SHALL emit one onChange only if displayed or submitted content differs, and SHALL NOT submit.

#### Scenario: Restore with stale work
- **WHEN** a draft is restored during an unfinished paste or autocomplete request
- **THEN** stale paste chunks and autocomplete results cannot overwrite the restored draft and external effects run after the state boundary is released
