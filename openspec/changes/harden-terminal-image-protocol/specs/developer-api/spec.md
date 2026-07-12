## MODIFIED Requirements

### Requirement: Public image APIs are stable and dependency-light
The developer API SHALL expose public shared-core `scalatui.terminal.Base64ImagePayload` and typed `scalatui.terminal.Base64ImagePayloadError` values plus optional image-module renderer helpers without requiring platform-specific dependencies at call sites. `scalatui.terminal.Base64ImagePayload.from(String)` SHALL return `Either[scalatui.terminal.Base64ImagePayloadError, scalatui.terminal.Base64ImagePayload]`, `scalatui.terminal.Base64ImagePayload.encode(Array[Byte])` SHALL return a validated payload, and the error model SHALL include `InvalidStandardBase64`. `scalatui.image.Image.fromBase64` SHALL be the high-level raw-string factory and return `Either[scalatui.terminal.Base64ImagePayloadError, Image]`. **BREAKING**: protocol helpers and `Image` SHALL require `scalatui.terminal.Base64ImagePayload` rather than raw base64 `String`, and `ImageSource` SHALL store the validated payload; no compatibility overload, adapter, fallback path, or deprecation path SHALL be provided.

#### Scenario: Applications can construct image options and helpers
- **WHEN** an application depends on the optional image module and configures image rendering options
- **THEN** it can do so from `scalatui` public types only, without JVM/Native-specific import branches

#### Scenario: Application validates a raw payload
- **WHEN** an application supplies a raw base64 string through `scalatui.terminal.Base64ImagePayload.from` or `scalatui.image.Image.fromBase64`
- **THEN** it receives either a validated payload or a typed `scalatui.terminal.Base64ImagePayloadError` before any image component or protocol sequence is produced

#### Scenario: Application encodes image bytes
- **WHEN** an application passes image bytes to `scalatui.terminal.Base64ImagePayload.encode`
- **THEN** it receives a validated standard-base64 payload usable by protocol helpers and `Image`

#### Scenario: Breaking raw-string paths are removed
- **WHEN** existing source passes raw base64 strings directly to protocol helpers or `Image`, or reads the `ImageSource` payload as a `String`
- **THEN** that source must migrate to `scalatui.terminal.Base64ImagePayload` or `scalatui.image.Image.fromBase64` because no compatibility or deprecation path exists

#### Scenario: Public API does not force terminal backend coupling
- **WHEN** applications run on JVM or Scala Native
- **THEN** validated image types and markdown/autocomplete models remain usable without depending on concrete terminal backend classes

#### Scenario: Core-only applications avoid image dependencies
- **WHEN** an application does not opt into image rendering modules
- **THEN** it can use `core` terminal capability and validated image payload APIs without transitively depending on image decoding, scaling, media, or third-party base64 libraries

### Requirement: Documentation and Scaladoc for public APIs
Changes that add or modify public APIs SHALL include project documentation and Scaladoc describing contract, platform scope, important non-goals, and source-breaking migration where applicable.

#### Scenario: Public API documentation accompanies implementation
- **WHEN** a change adds a public type or public method intended for application use
- **THEN** the implementation includes Scaladoc and the project documentation explains the capability or explicitly records why no documentation update is needed

#### Scenario: Breaking image API documentation accompanies implementation
- **WHEN** validated image payload types replace raw-string image signatures and fields
- **THEN** Scaladoc, examples, and project documentation name `scalatui.terminal.Base64ImagePayload`, `scalatui.terminal.Base64ImagePayloadError`, and `scalatui.image.Image.fromBase64`, and explain typed validation, typed failure, JVM/Native scope, validation memory cost, and required source migration without advertising compatibility work

#### Scenario: Future changes preserve documentation expectations
- **WHEN** a future OpenSpec change proposes public API or user-visible behavior
- **THEN** its tasks include documentation and Scaladoc work before validation is considered complete
