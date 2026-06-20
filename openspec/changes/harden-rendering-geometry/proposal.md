## Why

Most remaining renderer parity risk is in terminal geometry edge cases rather than missing components. Tabs, wide CJK cells, overlays, and zero-width rendering need direct parity tests so applications do not produce corrupted terminal output under realistic content.

## What Changes

- Match upstream tab display width as 3 unless existing project documentation already promises a different value.
- Harden ANSI-aware measuring, slicing, wrapping, truncation, and padding around tabs and wide grapheme clusters.
- Add overlay composition tests for boundaries that start inside or adjacent to wide CJK cells.
- Add shrink-to-zero and narrow-width regression coverage.
- Keep the change focused on internal correctness unless a public helper is required for documented behavior.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `component-rendering`: ANSI geometry, overlay composition, tab width, wide-cell boundaries, and narrow/shrink rendering behavior.
- `developer-api`: documentation expectations if any public ANSI helper behavior is clarified or exposed.

## Impact

- Affected modules: `core`, renderer tests, Unicode/ANSI utility tests, virtual terminal tests, docs if public behavior is clarified.
- Public API impact: none expected; any public helper changes require Scaladoc and docs.
- Dependency impact: no new runtime dependencies.
- Platform impact: shared behavior must remain identical for JVM and Scala Native modules.
