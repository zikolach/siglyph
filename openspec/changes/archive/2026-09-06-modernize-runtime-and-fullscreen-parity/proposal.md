## Why

Siglyph's runtime is reliable under its tested paths, but unexpected terminal worker failure and scheduler-driven component mutation lack complete lifecycle and threading contracts. Current `pi-tui` has also moved beyond buffer switching to a height-aware alternate-screen viewport, so Siglyph needs a current compatibility baseline and a deliberate fullscreen architecture before adding more behavior to the existing runtime.

## What Changes

- Propagate unexpected input, fragment-flush, and resize-worker failures into the owning TUI lifecycle so `run()` fails, cleanup completes, and callers do not wait indefinitely.
- Define one cross-platform component threading contract. Serialize built-in component mutation with rendering, and invoke application callbacks and autocomplete providers outside component and runtime locks.
- Add an alternate-screen viewport renderer with height-aware vertical and horizontal stacks, constrained allocation, nested scrolling, follow-end behavior, pointer-targeted wheel scrolling, and keyboard viewport navigation.
- Add fullscreen transcript search, jump-to-end behavior, scrollbars, and text selection in staged layers above the viewport foundation.
- Extend typed mouse input with movement, click, drag capture, focus requests, and reusable `MouseRegion` behavior while preserving opt-in reporting and coordinate-safe routing.
- Separate normal-screen and viewport rendering policies behind shared lifecycle, input, query, overlay, and terminal-output services without breaking existing `TUI(terminal)` construction.
- Add instance-scoped terminal capability overrides and current Zed detection without process-global mutable capability state.
- Add deterministic performance benchmarks and regression thresholds for large transcripts, differential rendering, Unicode wrapping, overlays, scrolling, and image-heavy frames.
- Refresh the pinned `pi-tui` baseline, compatibility matrix, README claims, public Scaladoc, examples, and interactive smoke coverage.
- Keep Node timers, `AbortSignal`, process-based completion, global mutable capability overrides, V8-specific output workarounds, Windows native helpers, and built-in LaTeX parsing out of scope.
- Add no third-party runtime dependency.

## Capabilities

### New Capabilities

- `fullscreen-viewport`: Height-aware alternate-screen layout, scrolling, transcript navigation, search, scrollbars, jump-to-end behavior, and selection.

### Modified Capabilities

- `terminal-runtime`: Propagate backend worker failures, separate rendering policy from shared lifecycle services, and support instance-scoped capability overrides and current terminal detection.
- `component-rendering`: Define height-constrained viewport composition, renderer policy boundaries, callback isolation, and performance expectations while preserving the existing width-only component fallback.
- `mouse-input`: Add typed motion, click, drag capture, focus requests, reusable mouse regions, viewport scrolling, and fullscreen selection behavior.
- `keybinding-management`: Add backend-independent viewport scrolling, transcript navigation, search, and selection commands.
- `image-rendering`: Clip and cache typed image placements correctly across viewport and scroll boundaries and use instance-scoped capability decisions.
- `developer-api`: Expose source-compatible fullscreen construction, document component threading and callback contracts, refresh current parity claims, and add benchmark and documentation requirements.

## Impact

- Runtime and rendering: `core/src/scalatui/core/TUI.scala`, lifecycle and renderer policy helpers, layout models, overlays, append-only output, and `VirtualTerminal`.
- Components and input: `Component`, `Container`, new stack and scroll components, `Editor`, `Input`, selectors, loaders, mouse parsing, routing, and keybindings.
- Backends: `StreamTerminal`, `SttyTerminal`, and `PosixTerminal` failure reporting and worker ownership.
- Images and capabilities: terminal capability detection, image placement clipping, viewport image retention, and optional image rendering.
- Tests and performance: focused JVM and Native lifecycle tests, viewport and mouse suites, PTY coverage, deterministic benchmarks, and regression thresholds.
- Documentation: README, compatibility matrix, porting notes, runtime and smoke documentation, examples, and public Scaladoc.
- Compatibility: Existing normal-screen construction and width-only components remain valid. New fullscreen behavior is opt-in. No runtime dependency is added.
