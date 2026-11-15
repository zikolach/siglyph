## Context

Review priority 2 identifies graphics ordering and ED2/upload lifecycle risks.
Pinned upstream: `8636e211e7834e4ca46a22aec1d65dfd2eac8677`; fixture locations
and scope are recorded in `docs/pi-graphics-lifecycle.md`.
Four deterministic regressions failed before implementation; overlay restoration
already passed and requires no subsystem rewrite.

## Goals / Non-Goals

Goals: image-last fullscreen painting, footprint-aware redraw, upload-safe full
clears, and JVM/Native regression coverage with unchanged public APIs.
Non-goals: pixel clipping, protocol profile expansion, normal-screen refactoring,
TUI dispatch changes, or real-emulator parity claims.

## Decisions

1. Keep text/EL painting and graphics in separate passes in the existing bounded
   FrameOutput. Select controls whose footprint intersects the painted suffix,
   even when their anchor precedes it. Preserve control ordering and final cursor.
2. Let retention reuse a matching Kitty upload when changed text intersects its
   painted footprint. Delete stale placements and restore after text; keep the
   existing entry/age limits, payload identity checks, and cleanup ownership.
3. Suppress ED2 whenever retention owns reusable uploads, including hidden ones.
   Existing per-row EL clears all current viewport rows during full redraw.
   Retransmitting all uploads instead would waste bandwidth and lose hidden data.
4. Preserve the already-correct overlay suppression/uncover path and test it.

## Risks / Trade-offs

Suffix painting can conservatively repaint more than the directly changed rows.
This matches existing row invalidation and avoids introducing a new damage model.
Tests assert encoded output ordering and reuse, not emulator pixel composition.
Partial pixel clipping remains unsafe without validated source-pixel geometry.

## Migration Plan

No API migration. Run focused suites on JVM and Native and strict OpenSpec
validation. Reverting these two implementation files restores the old policy.
