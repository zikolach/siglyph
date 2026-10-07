## Why

Fullscreen layout currently recomposites an empty base for every visible full-width leaf row. That work repeats ANSI/grapheme segmentation after the row was already sliced, even though no earlier paint needs preserving. Upstream `pi-tui` commit `18dee5f0a89f41466e876cbbbfe77635cd250882` uses direct row reuse in this geometry, but Siglyph must retain its stricter text sanitization and metadata closure.

## What Changes

- Give `Layout.paintFrame` a full-width, untouched-row path that skips empty-base composition while preserving the exact supported ANSI/OSC8 and clipping semantics.
- Keep partial-width, already-painted, later-overlay, decoration, typed control, cursor, and document-marker paths unchanged.
- Add shared JVM/Native regression coverage and one fixed benchmark workload with before/after measurements.

## Capabilities

### Modified Capabilities

- `component-rendering`: Bound redundant work for a full-width row without changing rendered bytes or semantic metadata.

## Impact

Shared `core` layout painter and tests, shared benchmark workload, and the component-rendering specification. No new dependency or public API.
