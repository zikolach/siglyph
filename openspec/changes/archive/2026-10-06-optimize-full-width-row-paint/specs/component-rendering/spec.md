## ADDED Requirements

### Requirement: Full-width untouched viewport row paint retains terminal semantics
The viewport painter SHALL avoid redundant empty-base recomposition and final truncation for a prepared full-width row that remains untouched. The shortcut SHALL preserve byte-equivalent sanitized, width-bounded terminal text and SGR/OSC 8 replay and closure. Later paint and typed metadata SHALL retain their existing order and clipping.

#### Scenario: Full-width untouched row preserves trusted text boundaries
- **WHEN** a full-width box first paints a viewport row containing short or wide graphemes, unclosed SGR or OSC 8 state, or unsupported terminal controls
- **THEN** its output remains byte-equivalent to ordinary empty-base composition after final width clipping
- **AND** unsupported controls gain no authority and supported state is closed before the next row

#### Scenario: Later paint and metadata retain their order
- **WHEN** a child or decoration later paints over a full-width row carrying typed controls, cursor candidates, or document markers
- **THEN** later text composition and independent typed metadata translation and clipping retain their existing behavior
