## ADDED Requirements

### Requirement: Image fallback theme output remains width-safe
Image fallback theme callbacks SHALL run before the final ANSI-aware width constraint.

#### Scenario: Theme callback precedes width constraint
- **WHEN** an image fallback theme callback transforms fallback output
- **THEN** the callback runs before the final ANSI-aware width constraint is applied

#### Scenario: Visible or styled additions remain width-safe
- **WHEN** an image fallback theme callback adds visible or ANSI-styled content
- **THEN** the final themed fallback output remains within the requested visible width

#### Scenario: Width zero has no positive visible width
- **WHEN** themed image fallback output is requested at width zero
- **THEN** the final output has no positive visible width

### Requirement: Image render geometry has a fixed frame-row safety limit
The image component SHALL preserve image-control rendering for supported protocol geometry through exactly 10,000 frame rows. Supported protocol geometry above 10,000 frame rows SHALL use the existing readable image fallback, apply the existing theme callback, and then apply the existing ANSI-aware width constraint. Oversized geometry SHALL produce no image control, allocate no frame-row vector, and allocate or update no image id. The 10,000-row limit SHALL be fixed and SHALL NOT affect dimension sniffing.

#### Scenario: Exact frame-row boundary retains image control
- **WHEN** a supported image protocol produces geometry of exactly 10,000 frame rows
- **THEN** the component emits its existing image control and reserves exactly 10,000 ordinary frame rows

#### Scenario: Geometry above the frame-row boundary uses readable fallback
- **WHEN** a supported image protocol would produce geometry above 10,000 frame rows
- **THEN** the component returns the existing readable themed and width-safe fallback with no image control, frame-row vector allocation, or image-id allocation or update

#### Scenario: Maximum portrait metadata renders safely
- **WHEN** valid portrait metadata contains an `Int.MaxValue` pixel dimension and its render geometry exceeds 10,000 frame rows
- **THEN** rendering returns the existing readable fallback without attempting oversized frame allocation

#### Scenario: Dimension metadata remains unrestricted by render geometry
- **WHEN** dimension sniffing reads positive dimensions representable by `ImageDimensions`
- **THEN** the dimensions remain valid metadata regardless of the frame rows they could produce during rendering

## MODIFIED Requirements

### Requirement: Image dimension sniffing
The image module SHALL provide dependency-light dimension sniffing for common supported formats where practical, including PNG, JPEG, GIF, and WebP, with optional richer decoders isolated from the baseline module. Sniffers SHALL validate decoded dimensions as positive and representable before constructing `ImageDimensions` and SHALL report invalid dimensions through the existing typed `InvalidImage` failure. JPEG marker-loop and segment-bound arithmetic SHALL avoid overflow-prone additive comparisons while preserving existing typed messages and precedence.

#### Scenario: Dimensions are detected without rendering
- **WHEN** dimension sniffing is run on a supported image byte stream with positive representable dimensions
- **THEN** it returns pixel width and height without requiring a terminal backend or TUI runtime

#### Scenario: Invalid bytes are rejected
- **WHEN** dimension sniffing receives invalid image bytes
- **THEN** it returns a typed failure rather than throwing from component rendering

#### Scenario: Zero dimension is rejected
- **WHEN** a supported image header encodes zero for width or height
- **THEN** dimension sniffing returns the existing typed `InvalidImage` failure

#### Scenario: Negative dimension is rejected
- **WHEN** a supported signed dimension field decodes to a negative width or height
- **THEN** dimension sniffing returns the existing typed `InvalidImage` failure

#### Scenario: Non-representable dimension is rejected
- **WHEN** a supported image header encodes a width or height outside the representation accepted by `ImageDimensions`
- **THEN** dimension sniffing returns the existing typed `InvalidImage` failure instead of narrowing, wrapping, or clamping the value

#### Scenario: JPEG bounds are overflow-safe
- **WHEN** JPEG marker or segment bounds are evaluated near the maximum integer offset
- **THEN** scanning uses overflow-safe available-byte checks without changing typed failure messages or their precedence
