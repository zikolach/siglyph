## Why

Fullscreen row erases and text can destroy image cells after graphics placement.
Full clears can discard Kitty uploads that the retention policy plans to reuse.
Pinned upstream regression fixtures provide deterministic checks of these risks.

## What Changes

- Add failing-first graphics lifecycle regressions and an overlay restoration control.
- Paint fullscreen graphics after text and invalidate intersecting image footprints.
- Avoid ED2 while Kitty uploads are retained; clear viewport rows instead.
- Document upstream provenance, retained invariants, and deferred pixel clipping.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `component-rendering`: fullscreen graphics ordering and retained-upload lifecycle.

## Impact

Changes are confined to fullscreen renderer policy, Kitty retention, focused tests,
and documentation. Public APIs, typed controls, bounded output, serialized writes,
normal-screen rendering, and conservative clipping remain unchanged.
