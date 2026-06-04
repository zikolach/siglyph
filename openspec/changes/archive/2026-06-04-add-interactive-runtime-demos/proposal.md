## Why

`scala-tui` now has renderable components and terminal backends, but the primary demo is still non-interactive and the interactive path is not robust enough for real applications. The next step is to prove the runtime across both JVM and Scala Native with live demos, safe terminal lifecycle handling, and input buffering comparable to `pi-tui`'s interactive foundation.

## What Changes

- Add a shared interactive runtime path for `TUI` that can run until requested exit and safely stop on Escape, Ctrl+C, or exceptions.
- Add input buffering so fragmented escape sequences and bracketed paste chunks are normalized before parsing.
- Normalize raw control bytes (for example Ctrl+C, Ctrl+A, Ctrl+E, Ctrl+W) into typed `TerminalInput` events.
- Enable and disable bracketed paste as part of JVM and Native terminal lifecycle.
- Add a shared interactive demo UI with both JVM and Scala Native launch targets.
- Harden terminal shutdown so cursor visibility, bracketed paste state, and raw terminal modes are restored reliably.
- Add tests for fragmented input, paste buffering, lifecycle behavior, and demo-relevant focus/render flow.

## Capabilities

### New Capabilities

### Modified Capabilities
- `terminal-runtime`: Add input buffering, control-key normalization, protocol lifecycle, safe shutdown, and interactive JVM/Native runtime behavior.
- `component-rendering`: Add interactive render scheduling/coalescing and focus flow requirements for live input.
- `developer-api`: Add public interactive run/exit APIs and JVM/Native demo commands.
- `text-editing`: Ensure the existing `Input` component works correctly with real normalized control keys, bracketed paste, and live focus updates.

## Impact

- Affects `core` runtime APIs, terminal input handling, `StreamTerminal`, JVM `SttyTerminal`, Native `PosixTerminal`, and demos.
- Adds or updates tests in the core test suite and likely adds demo modules/targets in `build.mill`.
- Does not introduce new runtime dependencies.
- Does not implement multiline `Editor`, autocomplete, overlays, Loader, or Markdown rendering; those remain post-runtime work.
