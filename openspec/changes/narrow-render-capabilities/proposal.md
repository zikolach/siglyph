## Why

The common renderer policy advertises transcript operations that fullscreen cannot support. Components also need a narrow read-only rendering service instead of depending on the TUI runtime.

## What Changes

- Extract internal append publishing and resize recovery preparation capabilities from the common renderer contract.
- Move prepared resize recovery data into a renderer-neutral internal type.
- Remove fullscreen unsupported-operation stubs while preserving output bytes and public APIs.
- Introduce a read-only RenderServices interface and migrate the image consumer (parent integration work).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `component-rendering`: narrow internal renderer capabilities and component rendering services without changing public behavior.

## Impact

Internal renderer policies, TUI capability dispatch, rendering services, image consumer, and focused renderer tests. No new dependencies or public API removals.
