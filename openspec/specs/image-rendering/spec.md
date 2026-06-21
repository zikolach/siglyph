# image-rendering Specification

## Purpose
Defines terminal image capability detection, Kitty/iTerm2 image protocol output, optional image helper module behavior, fallback rendering, cache-aware cleanup, and width-safe mixed output.
## Requirements
### Requirement: Image component for Kitty/iTerm2 protocols
The library SHALL expose core image capability/protocol contracts and an optional image component/helper module that can emit terminal image escapes for Kitty and iTerm2 when supported.

#### Scenario: Render image with supported protocol
- **WHEN** an application opts into the image module and terminal image capability is detected as Kitty or iTerm2
- **THEN** the component renders a protocol-specific image payload and reserves the reported image rows in its terminal output contract

#### Scenario: Fallback when protocol is unsupported
- **WHEN** the selected terminal reports no image protocol
- **THEN** the component renders a readable fallback row containing image metadata such as filename, size hint, or placeholder text

#### Scenario: Core remains dependency-light
- **WHEN** an application depends only on `core`
- **THEN** image protocol capability types remain available for runtime decisions without pulling image decoding or third-party image libraries into the application

#### Scenario: Image helpers can use optional dependencies
- **WHEN** image dimension parsing, file loading, scaling, or richer media handling requires third-party libraries
- **THEN** those helpers live in optional image modules and preserve the same component/protocol output contract

### Requirement: Protocol output is capability- and cache-aware
Image emission SHALL allow reuse/cleanup through image identifiers where protocol supports it and never attempt destructive image operations when unsupported.

#### Scenario: Reuse image handles
- **WHEN** an image source is updated and a prior image ID exists
- **THEN** the component may reuse or replace the existing terminal image identifier according to protocol behavior

#### Scenario: No-op cleanup on unsupported terminals
- **WHEN** cleanup helpers are invoked on terminals without image support
- **THEN** no unsupported escape sequence is emitted

### Requirement: Image rendering remains width-safe in mixed output
Image-capable lines and fallback text SHALL participate in existing width-sanitization semantics.

#### Scenario: Component output participates in line sanitization
- **WHEN** an image component renders in a frame with explicit width constraints
- **THEN** each returned line (including fallback content) is width-truncated or otherwise constrained by visible-width checks

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

### Requirement: Warp Kitty image capability detection
Terminal capability detection SHALL treat Warp as Kitty-image capable when not running inside tmux, screen, or another known unsupported multiplexer path.

#### Scenario: Warp TERM_PROGRAM enables Kitty images
- **WHEN** environment detection sees Warp terminal indicators outside a multiplexer
- **THEN** terminal capabilities report Kitty image support

#### Scenario: Warp session variable enables Kitty images
- **WHEN** environment detection sees `WARP_SESSION_ID` or `WARP_TERMINAL_SESSION_UUID` outside a multiplexer
- **THEN** terminal capabilities report Kitty image support

#### Scenario: Multiplexer disables Warp image support
- **WHEN** Warp indicators are present but the environment is inside tmux or screen without supported forwarding
- **THEN** terminal capabilities do not report image support

### Requirement: Cell-size-aware image sizing
Image sizing that opts into runtime terminal cell dimensions SHALL use queried dimensions when valid data is available and SHALL fall back to deterministic default cell dimensions when unavailable. Image sizing with fixed `ImageRenderOptions.cellDimensions` SHALL use those fixed dimensions exactly. The high-level `Image` component SHALL opt into runtime sizing by default because it renders inside a `TUI` lifecycle that owns terminal cell-size queries.

#### Scenario: Valid cell dimensions affect image rows
- **WHEN** image cell-size calculation receives valid terminal cell pixel dimensions
- **THEN** calculated image cell rows preserve image aspect ratio using those dimensions

#### Scenario: Missing cell dimensions use default
- **WHEN** runtime image sizing has no valid terminal cell dimensions available
- **THEN** image sizing uses the documented default cell dimensions

#### Scenario: Fixed cell dimensions remain deterministic
- **WHEN** image sizing receives fixed `ImageCellDimensions`
- **THEN** image sizing uses those fixed dimensions without reading cached runtime dimensions

#### Scenario: Invalid cell dimensions are ignored
- **WHEN** terminal cell dimensions are zero, negative, or malformed
- **THEN** image sizing ignores them and uses default dimensions

### Requirement: Image row reservation remains cursor-safe
Image rendering SHALL reserve terminal rows consistently so text rendered after terminal-owned image output appears below the image area.

#### Scenario: Kitty image reserves reported rows
- **WHEN** a Kitty image render result reports a positive row count
- **THEN** the component output reserves that many terminal rows before later content is rendered

#### Scenario: iTerm2 image reserves reported rows
- **WHEN** an iTerm2 image render result reports a positive row count
- **THEN** the component output reserves that many terminal rows before later content is rendered

#### Scenario: Fallback image row is width-safe
- **WHEN** image protocol support is unavailable
- **THEN** fallback text renders as readable width-safe terminal output

