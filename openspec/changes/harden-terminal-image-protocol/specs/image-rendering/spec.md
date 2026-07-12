## ADDED Requirements

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

## MODIFIED Requirements

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
