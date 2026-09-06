## ADDED Requirements

### Requirement: Viewport image clipping
The viewport renderer SHALL clip typed image placements to assigned layout and scroll-view rectangles. A typed image control SHALL execute only when its complete declared footprint is visible within every applicable clip boundary, unless the protocol helper can produce a separately validated cropped control.

#### Scenario: Image crosses sticky region
- **WHEN** a scrolled image footprint would cross into a sticky editor or footer rectangle
- **THEN** the viewport does not execute image cells through the sticky region

#### Scenario: Fully clipped image is absent
- **WHEN** an image placement is outside the visible viewport clip
- **THEN** its transmission control is absent from the prepared frame while required cleanup state remains correct

#### Scenario: Partial control is not executed
- **WHEN** clipping exposes only part of a typed image footprint and no validated crop operation exists
- **THEN** the control is omitted rather than emitted with inconsistent text-row reservation

### Requirement: Viewport Kitty image retention
The alternate-screen renderer SHALL retain bounded metadata for recently offscreen Kitty images so scrolling or layout movement does not retransmit unchanged payload data unnecessarily. Retention SHALL remain per TUI instance and SHALL preserve deterministic cleanup.

#### Scenario: Recently offscreen image returns
- **WHEN** an unchanged Kitty image scrolls out of view and returns within the retention bound
- **THEN** the renderer reuses valid retained image data and updates placement without retransmitting the payload

#### Scenario: Retention bound evicts image
- **WHEN** retained offscreen image metadata exceeds its configured count or age bound
- **THEN** the oldest eligible entry is evicted and later reuse follows normal typed retransmission and cleanup rules

#### Scenario: Runtime stop clears retained state
- **WHEN** the TUI stops after retaining offscreen Kitty image metadata
- **THEN** cleanup remains idempotent and no retained image state leaks into a later runtime instance

### Requirement: Effective capabilities remain session-owned
Image components in viewport and normal-screen renderers SHALL use the effective detected and explicitly overridden capabilities of their attached TUI session.

#### Scenario: Session disables images
- **WHEN** a TUI session explicitly disables image capability
- **THEN** attached images use readable fallback output in both normal-screen and viewport rendering

#### Scenario: Concurrent sessions differ
- **WHEN** concurrent sessions select different image protocol overrides
- **THEN** each image component emits only the protocol selected by its owning session
