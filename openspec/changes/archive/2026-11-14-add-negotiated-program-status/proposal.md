## Why

Applications need typed root program status without assuming OSC 7501 support or breaking Terminal implementations. New terminal environment hints also need explicit conservative profiles.

## What Changes

- Add optional negotiated OSC 7501 status, bounded encoding, serialized TUI reports and retryable cleanup.
- Consume valid late replies without dispatching them to applications.
- Add Herdr, Rex, explicitly advertised Orca images and Alacritty fallback detection.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `terminal-runtime`: Optional negotiated status and conservative terminal profiles.

## Impact

Shared core, JVM/Native interactive backends, focused tests and documentation. No dependencies, executors or new abstract Terminal methods. OSC 9;4 remains unchanged.
