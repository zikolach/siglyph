## ADDED Requirements

### Requirement: Revision-aware atomic viewport publication

The shared viewport layout engine SHALL capture the scroll document revision before measurement and submit an immutable publication containing rendered rows, complete document metadata, child identity, revision, and measured geometry. ScrollView SHALL reject publications for a replaced child or invalidated revision before mutating final document state, and SHALL publish accepted metadata and selection mapping atomically within its component state boundary.

#### Scenario: Content changes while rendering

- **WHEN** a scroll document is replaced or its revision is invalidated during row rendering or metadata collection
- **THEN** the old publication SHALL NOT replace current metadata, selection mapping, or final geometry

#### Scenario: Content changes while measuring

- **WHEN** measuring a child replaces that child or invalidates its revision
- **THEN** ScrollView SHALL reject the old child's early geometry clamp

#### Scenario: Early clamping remains available

- **WHEN** current content geometry is measured before rendering a bounded row range
- **THEN** ScrollView SHALL clamp the offset before range rendering and retain that early clamp if later invalidation rejects final publication

#### Scenario: Existing provider implementation

- **WHEN** a viewport provider implements only the existing geometry, metadata, and text callbacks
- **THEN** default snapshot and conditional geometry methods SHALL delegate to those callbacks without requiring source changes
