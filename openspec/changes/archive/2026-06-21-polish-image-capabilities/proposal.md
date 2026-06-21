## Why

Image protocol support exists, but terminal capability detection and image geometry can still diverge from upstream `pi-tui`. Warp support and cell-size query handling are the next useful steps for better terminal image behavior without adding image-processing dependencies.

## What Changes

- Treat Warp as Kitty-image capable outside tmux or other known unsupported multiplexers.
- Keep multiplexer constraints explicit for Kitty image capability decisions.
- Add terminal cell-size query and response handling for image sizing decisions.
- Ensure cell-size protocol replies are consumed by runtime/capability logic before component input routing.
- Harden image row reservation and cursor movement behavior around terminal-owned image output.
- Keep color-scheme and background color queries out of this change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `image-rendering`: Warp Kitty-image capability, cell-size-aware image sizing, image row reservation, and cursor behavior.
- `terminal-runtime`: terminal cell-size query/response parsing and protocol reply routing for image capability decisions.

## Impact

- Affected modules: `core`, `image`, terminal capability tests, virtual terminal tests, README/docs.
- Public API impact: possible additions to capability query results or image sizing helpers.
- Dependency impact: no new runtime dependencies.
- Platform impact: behavior must be testable without a live terminal and degrade safely when replies are absent.
