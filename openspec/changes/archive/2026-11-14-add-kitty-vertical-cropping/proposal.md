# Add typed vertical Kitty image cropping

## Why
Partially visible images currently disappear at scroll boundaries. Graphics-last painting and retained-upload lifecycle fixes are merged, making pixel-aware vertical cropping the next bounded extension.

## What Changes
- Carry positive declared source dimensions and immutable typed crop geometry without changing existing control-details pattern-match arity.
- Crop top/bottom scroll boundaries with validated numeric Kitty source rectangles; compose nested clips from original geometry.
- Reuse uploaded data across changed crops and display geometry while preserving cleanup bounds.
- Preserve conservative omission for unknown source geometry, iTerm2 and horizontal clipping; overlay occlusion remains whole-image suppression.

## Capabilities
### New Capabilities
None.
### Modified Capabilities
- image-rendering: source-pixel-aware vertical clipping and retained placement reuse.
- component-rendering: separately validated crop controls remain valid within layout bounds.

## Impact
Shared core protocol/geometry/retention, optional Image integration, JVM/Native tests, documentation and emulator smoke. No new dependencies, decoding/transcoding, release or horizontal/overlay-occlusion cropping.
