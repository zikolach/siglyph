# Graphics redraw lifecycle

## Scope and upstream pin

Behavioral fixtures are adapted from pi-tui commit
`8636e211e7834e4ca46a22aec1d65dfd2eac8677`, locally pinned at
`.empryo/pi-upstream`: `packages/tui/test/tui-alt-screen.test.ts` lines
1154 (covered-row writes), 1191 (full redraw retention), 1224 (image-last),
and `packages/tui/test/overlay-terminal-image.test.ts` line 221 (uncover).
This does not update the older compatibility-matrix pin or claim emulator parity.

## Invariants

- Fullscreen text writes and row erases precede graphics placement. An unchanged
  anchor is not sufficient to skip a graphic when the painted suffix intersects
  its cell footprint.
- Kitty payload identity and geometry remain bounded by the existing retention
  policy. Covered-row repaint can reuse a matching upload with placement-only
  output and placement cleanup, not retransmission.
- Full redraws (forced or resized) use row erases instead of ED2 while uploads
  are retained, including temporarily hidden uploads. ED2 can discard an upload
  that no longer has a placement. Every current viewport row is still cleared.
- Overlays conservatively omit intersecting images; hiding the overlay restores
  the retained placement without re-upload. This behavior already passed before
  the changes and was not rewritten.
- Controls remain typed. FrameOutput keeps its 65,536-character chunk bound,
  surrogate safety, and serialized frame writes; no component escape strings or
  independent graphic writes are introduced.

## Verification and limits

`GraphicsLifecycleSuite` initially reproduced four failures: image-last ordering,
changed text below an unchanged anchor, forced-clear ED2, and resized-clear ED2.
Overlay restoration passed initially. Focused coverage also includes
`FullscreenViewportSuite` and `TUITypedControlSuite` on JVM and Native.
Tests inspect deterministic terminal output, not actual emulator pixels.

Known-dimension Kitty images now support [typed vertical cropping](kitty-vertical-cropping.md).
Unknown geometry, horizontal clips and iTerm2 retain conservative omission; partial-overlay
occlusion cropping remains deferred.
Normal-screen transcript rendering, capability detection, and TUI dispatch are
outside this change.
