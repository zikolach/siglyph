## ADDED Requirements

### Requirement: Typed vertical Kitty crop geometry
Kitty image controls with positive declared source dimensions SHALL support top and bottom clipping through validated immutable source-pixel rectangles. Crops SHALL use original display geometry and bounded Long arithmetic, without arbitrary escape-string rewriting.

#### Scenario: Image spans both viewport edges
- **WHEN** a known-dimension Kitty image extends above and below a scroll viewport
- **THEN** its visible rows contain a separately validated cropped control within the viewport
- **AND** source top is rounded down and source bottom is rounded up within declared image bounds

#### Scenario: Nested clipping
- **WHEN** another vertical clip is applied to an already cropped image
- **THEN** its source rectangle is derived from cumulative original-row offsets rather than intermediate rounded pixel coordinates

### Requirement: Conservative image crop fallback
Legacy Kitty controls without source dimensions, iTerm2 controls, and horizontally clipped footprints SHALL retain conservative omission for partial visibility. Existing full-image helper signatures and encoded full-image bytes SHALL remain compatible.

#### Scenario: Unknown geometry or unsupported crop
- **WHEN** a partial placement lacks source dimensions, uses iTerm2, or needs horizontal cropping
- **THEN** no partial image control is emitted

### Requirement: Cropped placement reuse
Retained Kitty uploads SHALL distinguish payload/source identity from display crop geometry. A changed crop or resized placement of unchanged known-source data SHALL reuse the upload and update placement, preserving metadata and cleanup ownership.

#### Scenario: Scrolling changes only the source rectangle
- **WHEN** scrolling changes the crop while image ID, payload and visible cell footprint remain the same
- **THEN** the renderer emits the new validated placement without retransmitting payload

#### Scenario: Runtime ID remapping
- **WHEN** a cropped control receives a runtime-owned image ID
- **THEN** its source dimensions and crop geometry survive unchanged
