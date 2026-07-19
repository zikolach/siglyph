## Why

The coordinate-aware mouse feature can lose nested targets, publish geometry that was never painted, and collapse extended mouse buttons onto primary-button identities. These review findings should be fixed before the feature is released so routed input consistently describes and targets the frame visible in the terminal.

## What Changes

- Retain translated descendant layout nodes when `Box` composes padded child frames.
- Publish base and overlay layout trees only after a render candidate passes resize validation and is committed to terminal output.
- Preserve unknown SGR mouse-button identity bits after removing modifier, motion, and wheel flags.
- Add focused regression coverage for boxed mouse targets, resize-invalidated renders, and extended mouse-button codes.
- Replace the archived mouse-input specification placeholder with a meaningful purpose and remove introduced end-of-file whitespace.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `component-rendering`: Clarify that padded vertical composition retains descendant bounds with the same normalized padding and row offsets used for rendered output.
- `mouse-input`: Require routing geometry to describe the latest committed terminal frame and preserve extended unknown SGR button identities.

## Impact

- Shared core rendering and layout retention in `Box` and `TUI`.
- Shared SGR mouse parsing and parser tests on JVM and Scala Native.
- TUI routing regression tests using virtual terminals and deterministic resize fakes.
- Promoted OpenSpec documentation for component rendering and mouse input.
- No new runtime dependencies and no breaking public API changes.
