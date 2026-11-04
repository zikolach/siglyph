## Why

The JVM and Native interactive backends duplicate protocol enable and retryable cleanup state. Sharing this state reduces drift without changing platform lifecycle policy.

## What Changes

- Introduce an internal shared interactive protocol session for bracketed paste, captured mouse tracking mode, and Kitty negotiation and cleanup.
- Delegate both interactive backends to the helper while retaining their cleanup order, failure aggregation, worker ownership, and platform restoration.
- Add deterministic fake-output regression tests for partial writes and retries.

## Capabilities

### New Capabilities

- `interactive-protocol-session`: shared retryable protocol obligations for interactive backends.

### Modified Capabilities

None.

## Impact

Internal core helper, SttyTerminal, PosixTerminal, and shared tests only. No public API or StreamTerminal/input-delivery changes.
