## ADDED Requirements

### Requirement: Runtime separates ordinary lines from semantic controls
The TUI runtime SHALL prepare, compare, and write ordinary rendered lines separately from typed semantic terminal controls. It SHALL NOT infer trusted controls from string prefixes or parse ordinary text into control authority.

#### Scenario: Ordinary line follows text policy
- **WHEN** a rendered ordinary line contains escape-looking image bytes
- **THEN** it remains ordinary text and is processed only by the active ordinary-line ANSI and width policy

#### Scenario: Typed control follows control policy
- **WHEN** a prepared frame contains a valid known `TerminalRenderControl`
- **THEN** the runtime validates its placement and encodes it through the exhaustive shared encoder during final buffer assembly

### Requirement: Typed control output preserves runtime ownership
Typed control encoding and output SHALL remain inside the existing serialized render owner, synchronized-output boundary, resize-generation check, terminal-write lock, and cleanup path.

#### Scenario: Resize invalidates control frame
- **WHEN** dimensions or resize generation change before a frame containing typed controls commits
- **THEN** no control from the stale frame is written and a forced redraw uses current dimensions

#### Scenario: Control write failure cleans up
- **WHEN** terminal output fails while writing a frame containing typed controls
- **THEN** the runtime records the failure and restores terminal state through the existing single-owner cleanup path
#### Scenario: Pure control reorder triggers output
- **WHEN** ordinary lines and control values are unchanged but the prepared control vector order changes
- **THEN** differential rendering selects the earliest row affected by the first ordered difference and rewrites controls in the new order

#### Scenario: Existing Kitty ID is cleaned before retransmission
- **WHEN** a Kitty `a=T` control in the rewritten range uses an ID present in the previous prepared frame
- **THEN** the runtime emits exactly one typed `a=d,d=I,i=<id>` cleanup before any replacement transmission

#### Scenario: Removed Kitty ID is cleaned without replacement
- **WHEN** an old Kitty ID is absent from the new prepared frame
- **THEN** the runtime emits exactly one typed `a=d,d=I,i=<id>` cleanup without retransmitting the old image

#### Scenario: New and out-of-range Kitty IDs are not cleaned
- **WHEN** a transmitted Kitty ID is new or an unchanged old ID is outside the rewritten row range
- **THEN** the runtime emits no lifecycle cleanup for that ID

#### Scenario: Kitty cleanup order is deterministic
- **WHEN** a partial, reorder, forced, resize, move, or replacement redraw retransmits multiple old Kitty IDs
- **THEN** the runtime emits one cleanup per ID in previous-frame control order before all replacement transmissions

#### Scenario: Direct terminal write remains explicit
- **WHEN** application code bypasses TUI and calls a terminal backend write method directly
- **THEN** that direct output is outside the component trust boundary and receives no component-render sanitization promise
