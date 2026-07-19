## Context

The coordinate-aware mouse change introduced a retained component layout tree, SGR mouse parsing, and routing against the latest frame. Review of the completed feature found three related correctness gaps: `Box` still renders descendants through the legacy leaf-only path, `TUI` publishes candidate layout trees before a resize-generation check accepts the candidate, and the parser reduces every non-wheel button identity to two bits. The archived mouse-input specification also retains generated placeholder text.

The repair crosses shared component composition, the serialized TUI work drain, shared JVM/Native parsing, tests, and promoted specifications. It must preserve the existing `Component.render(width): ComponentRender` contract and add no runtime dependencies.

## Goals / Non-Goals

**Goals:**

- Make a padded `Box` retain child and descendant bounds that exactly match its rendered padding and vertical composition.
- Ensure mouse routing always uses layout geometry belonging to the latest successfully committed visual frame.
- Preserve valid extended SGR button identities without retaining modifier, motion, or wheel flags in `MouseButton.Other`.
- Add deterministic regression coverage shared by JVM and Scala Native where applicable.
- Complete the promoted mouse-input specification documentation and remove whitespace introduced by the archived change.

**Non-Goals:**

- Change keyboard focus or add click-to-focus behavior.
- Add pointer-motion routing, drag semantics, or additional terminal mouse protocols.
- Change the public `ComponentRender`, `MouseAction`, or `MouseButton` shapes.
- Add third-party runtime dependencies or change backend lifecycle ownership.

## Decisions

### Decision 1: Make `Box.renderFrame` the canonical padded composition path

`Box` will override `renderFrame` and render each child through `child.renderFrame` with an origin translated by normalized horizontal padding, normalized vertical padding, and the accumulated child body row. It will validate each child render before translating its terminal controls and cursor placements, retain the returned child layout node in visual order, and return a parent node whose height matches the final padded output. `render(width)` will delegate to this layout-aware path and return its typed render.

This keeps text, metadata, and retained bounds on one geometry calculation. Keeping the current `render` implementation and independently reconstructing layout would duplicate row accounting and could let the two paths drift. Replacing `Box` with `Container` is not suitable because `Box` owns padding, styling, truncation, and metadata validation semantics that `Container` does not.

### Decision 2: Publish layout trees at the render commit boundary

`TUI.renderNow` will keep candidate base and overlay layouts local while it prepares and validates a frame. If terminal dimensions or `resizeGeneration` change during rendering, it will request the forced clear rerender and leave the previously committed layouts untouched. On the accepted path, it will perform the full render, partial render, or no-text-difference cursor update first, update the prepared-frame state as today, and then publish the candidate layouts.

The serialized drain means no routed input runs in the middle of a successful commit. Publishing after the accepted branch also prevents a terminal-write failure from making unpainted geometry observable. Blocking mouse ingress until the replacement rerender was considered, but retaining the last committed layout is simpler and keeps queued input aligned with what remains visible.

### Decision 3: Separate SGR action flags from button identity

The parser will continue decoding modifiers from bits 4, 8, and 16 and wheel direction from the wheel flag plus the low direction bits. For press and release actions, it will derive a button identity by removing the modifier, motion, and wheel flags from the complete non-negative SGR code. Exact identities 0, 1, and 2 map to `Left`, `Middle`, and `Right`; every other identity maps to `Other(identity)`.

Masking with only `3` is rejected because it aliases extended button identities such as 128 with primary buttons. Storing the unfiltered report code in `Other` is also rejected because consumers would then see modifiers and action flags mixed into button identity.

### Decision 4: Exercise the rejected-render interleaving deterministically

The routing regression will first commit a known frame, then use a controlled component or terminal fake to publish a resize and mouse ingress during the next render. The resize invalidates that candidate, and the queued mouse event is allowed to run before the forced replacement render. The assertion will verify that routing still uses the previously committed tree. This tests the scheduler interleaving without sleeps or real terminal timing.

Box coverage will assert both retained descendant coordinates and end-to-end mouse delivery through normalized padding. Parser coverage will include an extended identity with modifier flags so the test proves both preservation and flag removal.

## Risks / Trade-offs

- [Risk] `Box.renderFrame` could subtly diverge from existing padding, styling, cursor, or terminal-control behavior → Reuse one canonical composition path and retain the existing narrow-width and metadata validation tests.
- [Risk] Moving layout publication could leave stale geometry after a successful no-output render → Treat an accepted no-text-difference candidate as committed and publish its layouts after cursor handling.
- [Risk] Future SGR flags could be mistaken for identity bits → Keep the currently supported flag mask explicit and add focused tests documenting the intended boundary.
- [Risk] Retaining descendants through `Box` adds small per-frame allocations → Use the existing immutable layout-node model; the added nodes are required for routing and proportional to rendered children.

## Migration Plan

No data or API migration is required. Implement the shared-core changes, add regression tests, update promoted specification text, and run the full JVM/Native build, formatting, Scalafix, strict OpenSpec validation, and diff whitespace checks. The change can be rolled back as one additive correctness patch before release.

## Open Questions

None.
