## ADDED Requirements

### Requirement: Image file loading helper
The image module SHALL provide an optional helper that loads supported image files into the existing image component/protocol contract by producing base64 data, MIME type, and dimensions without changing the low-level `Image` constructor contract.

#### Scenario: PNG file loads into image source
- **WHEN** an application asks the helper to load a supported PNG file
- **THEN** the helper returns base64 image data, MIME type `image/png`, and detected pixel dimensions usable by the `Image` component

#### Scenario: Unsupported file reports failure safely
- **WHEN** an application asks the helper to load an unsupported or unreadable image file
- **THEN** the helper reports a typed failure and does not emit terminal escape output

### Requirement: Image dimension sniffing
The image module SHALL provide dependency-light dimension sniffing for common supported formats where practical, including PNG, JPEG, GIF, and WebP, with optional richer decoders isolated from the baseline module.

#### Scenario: Dimensions are detected without rendering
- **WHEN** dimension sniffing is run on a supported image byte stream
- **THEN** it returns pixel width and height without requiring a terminal backend or TUI runtime

#### Scenario: Invalid bytes are rejected
- **WHEN** dimension sniffing receives invalid image bytes
- **THEN** it returns a typed failure rather than throwing from component rendering

### Requirement: Image size bounding helpers
The image capability SHALL expose helpers to calculate terminal cell bounds from source dimensions, requested maximum width/height, and terminal cell dimensions before protocol output is emitted.

#### Scenario: Portrait image respects height cap
- **WHEN** a tall source image is rendered with a maximum height in cells
- **THEN** calculated cell size respects the height cap while preserving aspect ratio as closely as terminal cells allow

#### Scenario: Unknown terminal falls back to text
- **WHEN** file-loaded image data is rendered on a terminal without image protocol support
- **THEN** the component renders the existing readable fallback text rather than writing unsupported protocol escapes

### Requirement: Image helper dependencies remain optional
Image helpers that require third-party parsing, scaling, or transcoding libraries SHALL live in optional modules and preserve the same source-to-component contract.

#### Scenario: Core image protocol remains dependency-light
- **WHEN** an application depends only on `siglyph-core` and the baseline image component module
- **THEN** it does not receive mandatory image decoding or transcoding dependencies
