## Why

Selectors and settings ignore page commands already supported by Editor and focused-first viewport routing. Applications also need explicit deterministic wheel and path-word policies without changing defaults or resource limits.

## What Changes

- Share default select page command resolution and clamp page movement to filtered items.
- Add an opt-in bounded wheel controller with explicit event time and already-accelerated source override, applied once before nested routing.
- Add optional grapheme-based extra word characters for viewport double-click and word drag.
- Document explicit integration and test JVM and Native behavior.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `fullscreen-viewport`: focused list pages, explicit wheel normalization and configurable word selection.

## Impact

Shared core components, new policy helpers, focused tests and TUI wheel-policy integration; no dependencies, timers, global environment changes or Editor modifications. Constructors remain source-compatible through trailing default parameters. Existing retention and overscroll contracts remain intact.
