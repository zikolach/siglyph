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

## MODIFIED Requirements

### Requirement: Image dimension sniffing
The image module SHALL provide dependency-light dimension sniffing for common supported formats where practical, including PNG, JPEG, GIF, and WebP, with optional richer decoders isolated from the baseline module. Sniffers SHALL validate decoded dimensions as positive and representable before constructing `ImageDimensions` and SHALL report invalid dimensions through the existing typed `InvalidImage` failure.

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
