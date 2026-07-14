# image-rendering Specification

## Purpose
Defines terminal image capability detection, Kitty/iTerm2 image protocol output, optional image helper module behavior, fallback rendering, cache-aware cleanup, and width-safe mixed output.
## Requirements
### Requirement: Image component for Kitty/iTerm2 protocols
The library SHALL expose core image capability/protocol contracts and an optional image component/helper module that can emit terminal image escapes for Kitty and iTerm2 when supported. The component and protocol contracts SHALL require `scalatui.terminal.Base64ImagePayload` instead of raw base64 `String` values. `scalatui.image.Image.fromBase64` SHALL be the high-level raw-string component factory and SHALL return `Either[scalatui.terminal.Base64ImagePayloadError, Image]` before constructing output.

#### Scenario: Render image with supported protocol
- **WHEN** an application opts into the image module, supplies a validated payload, and terminal image capability is detected as Kitty or iTerm2
- **THEN** the component renders a protocol-specific image payload and reserves the reported image rows in its terminal output contract

#### Scenario: Reject invalid raw payload without rendering
- **WHEN** an application passes invalid base64 to `scalatui.image.Image.fromBase64`
- **THEN** the factory returns a typed `scalatui.terminal.Base64ImagePayloadError`, creates no `Image`, and emits no component or protocol output

#### Scenario: Fallback when protocol is unsupported
- **WHEN** the selected terminal reports no image protocol and the image has a validated payload
- **THEN** the component renders a readable fallback row containing image metadata such as filename, size hint, or placeholder text

#### Scenario: Core remains dependency-light
- **WHEN** an application depends only on `core`
- **THEN** image protocol capability and validated payload types remain available for runtime decisions without pulling image decoding or third-party image libraries into the application

#### Scenario: Image helpers can use optional dependencies
- **WHEN** image dimension parsing, file loading, scaling, or richer media handling requires third-party libraries
- **THEN** those helpers live in optional image modules and preserve the same validated component/protocol output contract

### Requirement: Protocol output is capability- and cache-aware
Image emission SHALL allow reuse/cleanup through image identifiers where protocol supports it and never attempt destructive image operations when unsupported.

#### Scenario: Reuse image handles
- **WHEN** an image source is updated and a prior image ID exists
- **THEN** the component may reuse or replace the existing terminal image identifier according to protocol behavior

#### Scenario: No-op cleanup on unsupported terminals
- **WHEN** cleanup helpers are invoked on terminals without image support
- **THEN** no unsupported escape sequence is emitted

### Requirement: Image rendering remains width-safe in mixed output
Image-capable lines and fallback text SHALL participate in existing width-sanitization semantics. Filename and MIME controls in fallback text SHALL be converted to visible escaped text before width sanitization.

#### Scenario: Component output participates in line sanitization
- **WHEN** an image component renders in a frame with explicit width constraints
- **THEN** each returned line, including fallback content after metadata escaping, is width-truncated or otherwise constrained by visible-width checks

### Requirement: Image file loading helper
The image module SHALL provide an optional helper that loads supported image files into the existing image component/protocol contract by producing a validated base64 payload, MIME type, and dimensions without changing image-format validation semantics.

#### Scenario: PNG file loads into image source
- **WHEN** an application asks the helper to load a supported PNG file
- **THEN** the helper returns a validated base64 image payload, MIME type `image/png`, and detected pixel dimensions usable by the `Image` component

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

### Requirement: Native baseline image artifact
The baseline image component, validated payload API, and dependency-light image helpers SHALL be published for Scala Native when their file, base64, protocol, and dimension-sniffing behavior can be compiled unchanged or behind a small shared portability boundary.

#### Scenario: Native application depends on image module
- **WHEN** a Scala Native application adds the Native `siglyph-image` artifact
- **THEN** it can construct validated image sources, calculate dimensions, and render the `Image` component through the same public API shape as JVM applications

#### Scenario: Native image uses shared rendering logic
- **WHEN** the Native image module is built
- **THEN** payload validation, protocol rendering, fallback rendering, and dimension-sniffing logic come from canonical shared source trees or shared compatibility code rather than a duplicated Native implementation

#### Scenario: Unsupported portability path blocks publication
- **WHEN** the image module cannot compile on Scala Native without duplicated logic, a leaky abstraction, or a new unapproved runtime dependency
- **THEN** the image Native artifact is not published by this change and the blocker is documented before implementation continues

#### Scenario: Native image keeps dependency-light baseline
- **WHEN** the Native image artifact is published
- **THEN** it does not add third-party image parsing, scaling, transcoding, or base64 runtime dependencies to the baseline image module

#### Scenario: Native protocol suite verifies parity
- **WHEN** core and image Native tests run
- **THEN** the shared terminal image protocol security cases and image component cases execute against the Native implementations

### Requirement: Terminal image payload validation precedes output
The library SHALL represent terminal image data as a validated `scalatui.terminal.Base64ImagePayload`. `scalatui.terminal.Base64ImagePayload.from(String)` SHALL return `Either[scalatui.terminal.Base64ImagePayloadError, scalatui.terminal.Base64ImagePayload]`, with `InvalidStandardBase64` for invalid standard base64, and `scalatui.terminal.Base64ImagePayload.encode(Array[Byte])` SHALL produce a validated payload without a runtime dependency. Validation SHALL accept only the standard alphabet (`A-Z`, `a-z`, `0-9`, `+`, `/`) and optional valid terminal `=` padding. Empty input SHALL be accepted. Decoder-valid unpadded input with length remainder two or three modulo four SHALL be accepted, while length remainder one SHALL return `InvalidStandardBase64`. Accepted text SHALL remain unchanged. These rules SHALL be identical on JVM and Scala Native. Protocol helpers SHALL accept only validated payloads.

#### Scenario: Padded standard base64 is accepted unchanged
- **WHEN** `scalatui.terminal.Base64ImagePayload.from` receives valid padded standard base64
- **THEN** it returns a validated payload that preserves the original text unchanged

#### Scenario: Remainder-two unpadded standard base64 is accepted unchanged
- **WHEN** `scalatui.terminal.Base64ImagePayload.from` receives decoder-valid unpadded standard base64 whose length remainder modulo four is two
- **THEN** it returns a validated payload that preserves the original unpadded text unchanged

#### Scenario: Remainder-three unpadded standard base64 is accepted unchanged
- **WHEN** `scalatui.terminal.Base64ImagePayload.from` receives decoder-valid unpadded standard base64 whose length remainder modulo four is three
- **THEN** it returns a validated payload that preserves the original unpadded text unchanged

#### Scenario: Remainder-one unpadded standard base64 is rejected
- **WHEN** `scalatui.terminal.Base64ImagePayload.from` receives unpadded input whose length remainder modulo four is one
- **THEN** it returns `InvalidStandardBase64`

#### Scenario: Empty payload is protocol-valid
- **WHEN** `scalatui.terminal.Base64ImagePayload.from` receives an empty string
- **THEN** it returns a validated empty payload because image-format validity is a separate concern

#### Scenario: Unsafe or malformed payload is rejected before output
- **WHEN** a raw payload contains whitespace, controls, URL-safe alphabet characters, Kitty or iTerm2 framing terminators, invalid padding, or other invalid standard-base64 syntax
- **THEN** validation returns `InvalidStandardBase64`, creates no image component, and emits no protocol sequence

#### Scenario: Bytes encode to validated standard base64
- **WHEN** `scalatui.terminal.Base64ImagePayload.encode` receives an array of bytes
- **THEN** it returns their standard padded base64 encoding as a validated payload

### Requirement: Terminal image metadata is protocol-safe
The iTerm2 encoder SHALL encode a present filename's UTF-8 bytes as standard base64 in the `name=` field and SHALL omit the field when no filename exists. Fallback rendering SHALL convert controls in filename and MIME metadata to visible escaped text before ANSI-aware truncation.

#### Scenario: Unicode filename is encoded for iTerm2
- **WHEN** an iTerm2 image has a filename containing non-ASCII Unicode text
- **THEN** `name=` contains standard base64 of the filename's UTF-8 bytes

#### Scenario: Missing filename omits iTerm2 name field
- **WHEN** an iTerm2 image has no filename
- **THEN** the emitted sequence contains no `name=` field

#### Scenario: Fallback controls are visible and inert
- **WHEN** fallback filename or MIME metadata contains control characters
- **THEN** the fallback row contains visible escaped text, contains no executable control from that metadata, and is ANSI-aware truncated to the requested width

#### Scenario: Valid payload is unchanged in protocol output
- **WHEN** Kitty or iTerm2 output is constructed from a validated payload
- **THEN** the payload text in the emitted sequence exactly matches the validated payload text

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
