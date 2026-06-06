## ADDED Requirements

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
