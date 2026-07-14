## ADDED Requirements

### Requirement: Image components emit typed semantic controls
Kitty and iTerm2 image rendering SHALL return a semantic `TerminalRenderControl` and geometry instead of embedding protocol escape bytes in ordinary component lines. The `Image` component SHALL place that control in `ComponentRender` and reserve the reported rows with ordinary blank lines.

#### Scenario: Kitty image retains typed payload
- **WHEN** a validated payload renders for Kitty
- **THEN** the image result contains a typed Kitty control with unchanged payload text, validated dimensions and id, and no raw Kitty sequence in ordinary lines

#### Scenario: iTerm2 image retains typed payload and filename
- **WHEN** a validated payload and optional filename render for iTerm2
- **THEN** the image result contains a typed iTerm2 control whose final encoding preserves payload text and protocol-safe filename behavior, with no raw OSC 1337 sequence in ordinary lines

#### Scenario: Later content follows reserved rows
- **WHEN** a typed image component is followed by ordinary content in a TUI frame
- **THEN** the control executes at its anchor and the later content appears below every reserved image row
#### Scenario: Non-positive render width clips protocol output
- **WHEN** `Image.render` receives zero or negative available width
- **THEN** it returns width-safe empty ordinary output with no terminal control, protocol selection, image ID allocation, or unsupported-capability text

#### Scenario: Image control geometry is positive
- **WHEN** Kitty or iTerm2 control construction receives zero or negative cell width or height
- **THEN** construction rejects the control before terminal output

#### Scenario: Box composition has no zero-width image control
- **WHEN** a box leaves no content width for an image child
- **THEN** the child contributes no terminal control for the box to translate

#### Scenario: Unsupported protocol remains ordinary fallback
- **WHEN** terminal image capability is absent
- **THEN** the image component returns only readable width-safe fallback text and no typed terminal control

### Requirement: Image cleanup uses semantic controls
Kitty image cleanup helpers SHALL return typed cleanup controls rather than raw escape strings, and unsupported protocols SHALL return no cleanup control.

#### Scenario: Kitty cleanup is typed
- **WHEN** cleanup is requested for an allocated Kitty image id
- **THEN** the helper returns a semantic delete control whose encoding is owned by shared core

#### Scenario: Targeted Kitty cleanup deletes image data
- **WHEN** cleanup is requested for an allocated positive Kitty image ID
- **THEN** the helper encodes exactly `a=d,d=I,i=<id>` so the targeted image data and placements are removed before retransmission

#### Scenario: Delete-all Kitty cleanup is unchanged
- **WHEN** cleanup is requested for all Kitty images
- **THEN** the helper encodes `a=d,d=A`
#### Scenario: Kitty IDs are positive
- **WHEN** a configured, transmitted, or targeted-cleanup Kitty image ID is zero or negative
- **THEN** the typed API rejects it before terminal output

#### Scenario: Kitty ID allocation is exhausted
- **WHEN** the allocator has issued `Int.MaxValue`
- **THEN** the next allocation fails explicitly before output without wrapping or substituting an ID

#### Scenario: Unsupported cleanup emits nothing
- **WHEN** cleanup is requested without Kitty capability
- **THEN** no control or raw sequence is produced
