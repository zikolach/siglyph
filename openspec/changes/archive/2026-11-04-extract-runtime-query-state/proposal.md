## Why

TUI currently mixes terminal query flight and subscriber bookkeeping with lifecycle scheduling. Extracting that bookkeeping provides a bounded internal seam without changing public query behavior.

## What Changes

- Introduce an internal QueryCoordinator for flight identity, write reservations, subscriber transitions, cancellation, and completion snapshots.
- Keep lifecycle locking, terminal writes, ingress publication, callback execution, and cleanup scheduling in TUI.
- Add deterministic helper coverage alongside existing runtime query tests.

## Capabilities

### New Capabilities
- `runtime-query-state`: Internal query bookkeeping and its existing runtime integration contract.

### Modified Capabilities

None. Public APIs and observable behavior remain unchanged.

## Impact

Shared JVM/Native core sources only: TUI and the new QueryCoordinator, plus helper tests. No dependencies, locks, queues, or threads are introduced.
