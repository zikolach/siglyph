## Context

Scroll layout must clamp offset before rendering bounded ranges. Previously metadata committed without checking child/revision while text publication checked both. Measurement itself can invoke application code and invalidate the document.

## Goals / Non-Goals

- Reject obsolete child/revision before early geometry or final snapshot mutation.
- Publish metadata, bounded selection mapping, and measured geometry within one component state boundary.
- Preserve early clamping, follow-end, range bounds, and old provider source compatibility.
- Do not change runtime frame ownership or guarantee cancellation of an already rendered obsolete frame.

## Decisions

Capture revision before intrinsic measurement. Add a conditional geometry overload with a legacy-delegating default. Retain the original callbacks, and add a snapshot callback whose default forwards metadata and text to old implementations.

The immutable ViewportSnapshot carries rows/start/completeness, metadata, child identity, revision, geometry, and render offset. ScrollView builds bounded selection data outside its lock, then checks identity and revision before committing any final state. Complete selection state and metadata update under the same state boundary. Range-selection refresh remains outside locks and retains its own revision guard. The render offset records the frame's origin; publication does not rewind user scrolling that happened during rendering.

Early geometry is intentionally visible before final publication. Invalidation during rendering prevents final publication without undoing an already accepted early clamp. Legacy providers retain their previous semantics; strict conditional publication is provided by ScrollView.

## Risks / Trade-offs

Revision changes must still be signaled with replaceContent or invalidateSearchDocument. Same-revision concurrent frames are not ordered by this contract. Rows remain bounded by existing selection limits when mapped. Application render/metadata callbacks remain outside component locks.

## Validation

Focused snapshot tests cover current publication, replacement, same-child revision invalidation, mutation during measurement/rendering, and legacy defaults. Existing layout, fullscreen, selection, affordance, and search suites protect behavior.
