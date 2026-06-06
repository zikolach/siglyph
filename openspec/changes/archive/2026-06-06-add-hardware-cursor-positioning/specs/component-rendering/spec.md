## ADDED Requirements

### Requirement: Final frame cursor marker handling
The renderer SHALL treat cursor markers as zero-width runtime metadata during final frame preparation, after base content and overlays have been composited.

#### Scenario: Overlay composition determines marker visibility
- **WHEN** a base component emits a cursor marker but a visible overlay covers that cell in the final composited frame
- **THEN** hardware cursor marker scanning uses only the marker sequences that remain in the final composited output

#### Scenario: Marker does not affect line width contract
- **WHEN** a component line contains a cursor marker before a fake cursor token
- **THEN** component and runtime visible-width calculations treat the marker as non-printing metadata

#### Scenario: Differential rendering compares cleaned frames
- **WHEN** the TUI stores frame state for later differential rendering
- **THEN** it stores and compares marker-stripped lines so marker metadata does not leak into viewport snapshots or cause marker-only redraws
