## ADDED Requirements

### Requirement: Terminal controls retain typed provenance
The shared core SHALL represent executable component-owned terminal protocols as closed semantic `TerminalRenderControl` values placed separately from ordinary rendered strings. Concrete controls SHALL be constructible only through typed protocol APIs and SHALL NOT accept or expose an arbitrary trusted escape string.

#### Scenario: Typed protocol request retains authority
- **WHEN** an application obtains an image control through `TerminalImageProtocol` from validated domain inputs and places it in component output
- **THEN** the control retains typed provenance through composition and can be encoded at the final output boundary

#### Scenario: Matching ordinary text gains no authority
- **WHEN** an ordinary rendered string contains bytes matching Kitty, iTerm2, or another terminal-control grammar
- **THEN** those bytes do not become a `TerminalRenderControl` and receive no trusted-control treatment

#### Scenario: Control hierarchy is closed
- **WHEN** application code uses the public terminal-control API
- **THEN** it cannot construct a custom raw control variant or supply arbitrary escape bytes as a trusted value

### Requirement: Typed controls have validated frame placement
Each `TerminalControlPlacement` SHALL identify a non-negative frame-relative row and display column, and each control SHALL expose its display-cell footprint. A surviving control footprint SHALL fit entirely within the final frame and requested width before encoding.

#### Scenario: Valid placement is preserved
- **WHEN** a typed image control fits within its component frame and requested width
- **THEN** composition preserves its semantic fields, anchor, width, and reserved rows without converting it to a string

#### Scenario: Invalid final placement fails before output
- **WHEN** a surviving typed control has a negative anchor or extends outside the final frame or requested width
- **THEN** rendering fails before terminal write and normal runtime cleanup runs without moving, dropping, partially encoding, or converting the control to text

### Requirement: Validation and control diagnostics are bounded and confidential
Validation failures and `TerminalRenderControl.toString` SHALL retain and report only bounded semantic kind, optional image ID, coordinates, footprint, frame dimensions, and duplicate coordinates as applicable. They SHALL NOT retain or report a placement, control, payload, filename, or arbitrary application text.

#### Scenario: Geometry failure has a large sensitive payload or filename
- **WHEN** row, width, or overlay validation fails for a control with a large payload or filename
- **THEN** the typed error, exception, and their default strings contain no payload or filename content and have size independent of those values

#### Scenario: Duplicate ID failure has a large sensitive payload
- **WHEN** duplicate active Kitty ID validation fails for controls with large payloads
- **THEN** the typed error retains only the ID and bounded diagnostics for both coordinates and footprints

#### Scenario: Control string output is redacted
- **WHEN** `toString` is called on a Kitty image, iTerm2 image, or Kitty cleanup control
- **THEN** it contains only bounded kind, geometry, and positive ID where applicable, with no payload or filename content

### Requirement: Final frames have unique active Kitty image IDs
A final `ComponentRender` frame SHALL contain at most one active Kitty image control for each semantic integer image ID. Validation SHALL compare integer IDs without reconstructing or copying payload strings. Kitty cleanup controls SHALL NOT participate in this uniqueness rule.

#### Scenario: Duplicate active Kitty image ID fails before output
- **WHEN** two active Kitty image controls in one final frame use the same image ID
- **THEN** validation returns a typed duplicate-ID error and the TUI rejects the frame before synchronized terminal output

#### Scenario: Cleanup controls are not active placements
- **WHEN** a frame contains Kitty cleanup controls that refer to the same image ID
- **THEN** active-image uniqueness validation does not classify those cleanup controls as image placements
#### Scenario: Final image encoding revalidates positive fields
- **WHEN** final encoding receives image details with non-positive width, height, transmit ID, or targeted-cleanup ID
- **THEN** encoding rejects the control before producing terminal protocol bytes

### Requirement: Typed controls encode only at the final boundary
The TUI SHALL keep controls semantic through prepared-frame and differential state and SHALL encode them exhaustively only while assembling a synchronized terminal write.

#### Scenario: Full render emits known control
- **WHEN** a full frame contains a valid typed image control
- **THEN** the final synchronized write contains the protocol encoding at its declared anchor and preserves its reserved rows

#### Scenario: Differential render tracks semantic changes
- **WHEN** a typed control is added, removed, moved, or changes a semantic field
- **THEN** differential rendering marks the earliest affected row as changed and emits the required updated row range

#### Scenario: Unchanged control does not force redraw
- **WHEN** ordinary lines and typed controls are semantically unchanged
- **THEN** differential rendering performs no frame rewrite solely for that control

### Requirement: Typed terminal output is shared and dependency-free
`ComponentRender`, placement types, semantic controls, composition, validation, and encoding SHALL compile from canonical shared sources on JVM and Scala Native without a new runtime dependency.

#### Scenario: JVM and Native encode the same control
- **WHEN** JVM and Scala Native receive the same semantic control and placement
- **THEN** they produce the same protocol bytes and frame geometry
#### Scenario: Shared typed runtime suites execute on Native
- **WHEN** the Native test target selects typed TUI and overlay suites
- **THEN** their shared test cases execute instead of reporting zero tests

#### Scenario: Dependency graph remains unchanged
- **WHEN** the build is inspected after implementation
- **THEN** no new third-party runtime dependency is present
