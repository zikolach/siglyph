## MODIFIED Requirements

### Requirement: Cell-size-aware image sizing
Image sizing that opts into runtime terminal cell dimensions SHALL use valid dimensions from the TUI session currently attached to the image and SHALL fall back to deterministic default cell dimensions when unavailable. Runtime-derived dimensions SHALL NOT be read from or written to process-global mutable image geometry. Image sizing with fixed `ImageRenderOptions.cellDimensions` SHALL use those fixed dimensions exactly. The high-level `Image` component SHALL opt into attached-session sizing by default.

#### Scenario: Valid cell dimensions affect image rows
- **WHEN** an image attached to a running TUI receives valid terminal cell pixel dimensions from that session
- **THEN** calculated image cell rows preserve image aspect ratio using that session's dimensions

#### Scenario: Missing cell dimensions use default
- **WHEN** runtime image sizing has no attached session or no valid dimensions are available in the attached session
- **THEN** image sizing uses the documented default cell dimensions

#### Scenario: Fixed cell dimensions remain deterministic
- **WHEN** image sizing receives fixed `ImageCellDimensions`
- **THEN** image sizing uses those fixed dimensions without reading runtime or cached dimensions

#### Scenario: Invalid cell dimensions are ignored
- **WHEN** terminal cell dimensions are zero, negative, or malformed
- **THEN** image sizing ignores them and uses default dimensions

#### Scenario: Concurrent image sessions remain isolated
- **WHEN** image components in two concurrent TUIs use runtime sizing and the terminals report different cell dimensions
- **THEN** each image calculates its rows from only its attached TUI session and neither result changes the other

#### Scenario: Nested image receives its owning context
- **WHEN** an image using runtime sizing is nested inside a built-in composite component attached to a TUI
- **THEN** the composite propagates the owning TUI context so the image uses that session's dimensions
